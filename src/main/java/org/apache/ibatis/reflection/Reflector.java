/**
 *    Copyright 2009-2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  /**
   * 该对象对应的class类型
   * */
  private Class<?> type;
  /**
   * 可读属性名称，可读就是存在getter方法
   * */
  private String[] readablePropertyNames = EMPTY_STRING_ARRAY;
  /**
   * 可写属性名称，可写就是存在setter方法
   * */
  private String[] writeablePropertyNames = EMPTY_STRING_ARRAY;
  /**
   * 记录属性对应的setter方法，key为属性名称，value为Method对象的封装
   * */
  private Map<String, Invoker> setMethods = new HashMap<String, Invoker>();
  /**
   * 记录属性对应的getter方法，key为属性名称，value为Method对象的封装
   * */
  private Map<String, Invoker> getMethods = new HashMap<String, Invoker>();
  /**
   * 记录属性对应的setter方法参数，key为属性名称，value为方法参数类型
   * */
  private Map<String, Class<?>> setTypes = new HashMap<String, Class<?>>();
  /**
   * 记录属性对应的getter方法的参数，key为属性名称，value为返回值类型
   * */
  private Map<String, Class<?>> getTypes = new HashMap<String, Class<?>>();
  /**
   * 默认的构造方法
   * */
  private Constructor<?> defaultConstructor;

  /**
   * 记录所有属性名称（纯大写）的集合
   * */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<String, String>();

  public Reflector(Class<?> clazz) {
    type = clazz;
    //查找默认的构造方法
    addDefaultConstructor(clazz);
    //查找getter方法，该方法会填充getMethods和getTypes字段
    addGetMethods(clazz);
    //查找setter方法，该方法会填充setMethods和setTypes字段
    addSetMethods(clazz);
    //查找没有getter和setter方法的字段
    addFields(clazz);
    //根据getMethods、setMethods集合填充readablePropertyNames和writeablePropertyNames字段
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    //转换大写后填充caseInsensitivePropertyMap集合，在获取时也会将key转换为大写
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writeablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addDefaultConstructor(Class<?> clazz) {
    //获取所有的构造方法
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    //循环找到无参的方法并赋值
    for (Constructor<?> constructor : consts) {
      if (constructor.getParameterTypes().length == 0) {
        if (canAccessPrivateMethods()) {
          try {
            constructor.setAccessible(true);
          } catch (Exception e) {
            // Ignored. This is only a final precaution, nothing we can do.
          }
        }
        if (constructor.isAccessible()) {
          this.defaultConstructor = constructor;
        }
      }
    }
  }

  private void addGetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingGetters = new HashMap<String, List<Method>>();
    //获取该类和父类中所有的方法对应的method对象
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      //如果是get方法并且长度大于三（避免方法名只是get()，没有属性名称）
      if (name.startsWith("get") && name.length() > 3) {
        if (method.getParameterTypes().length == 0) {
          /*
          * 调用PropertyNamer.methodToProperty方法，通过substring获取属性名称并且首字母小写
          * 如getAbc()  这里就是abc     isBcd()  这里就是bcd
          * */
          name = PropertyNamer.methodToProperty(name);
          /*
          * 将method对象和属性名称记录到conflictingGetters集合中
          * 注意，conflictingGetters的value是list类型的，具体原因后面会解释
          * */
          addMethodConflict(conflictingGetters, name, method);
        }
      } else if (name.startsWith("is") && name.length() > 2) {
        //处理is方法
        if (method.getParameterTypes().length == 0) {
          name = PropertyNamer.methodToProperty(name);
          addMethodConflict(conflictingGetters, name, method);
        }
      }
    }
    /*
    * 之前说过，conflictingGetters的value是list类型的，原因如下
    * 当子类重写了父类的getter方法并修改了返回值类型的时候
    * 在addUniqueMethods()方法中就会产生两条数据
    * 如现有类A 及其子类SubA, A 类中定义了getNames()方法，其返回值类型是List<String>，
    * 而在其子类SubA 中， 覆写了其getNames()方法且将返回值修改成ArrayList<String>
    * 最终得到的两个方法签名分别是java.util.List#getNames和java.util.ArrayList#getNames
    * 所以conflictingGetters方法的value为list类型
    * resolveGetterConflicts就是要处理这种情况
    * */
    resolveGetterConflicts(conflictingGetters);
  }

  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (String propName : conflictingGetters.keySet()) {
      //获取属性对应的多个getter方法
      List<Method> getters = conflictingGetters.get(propName);
      Iterator<Method> iterator = getters.iterator();
      Method firstMethod = iterator.next();
      //如果只有一个getter方法，证明没有发生上面说的情况，直接添加get方法即可
      if (getters.size() == 1) {
        addGetMethod(propName, firstMethod);
      } else {
        Method getter = firstMethod;
        //记录返回值类型
        Class<?> getterType = firstMethod.getReturnType();
        while (iterator.hasNext()) {
          Method method = iterator.next();
          Class<?> methodType = method.getReturnType();
          //如果返回值相同，这种情况应该在addUniqueMethods()方法中被过滤掉，这里直接抛出异常
          if (methodType.equals(getterType)) {
            throw new ReflectionException("Illegal overloaded getter method with ambiguous type for property "
                + propName + " in class " + firstMethod.getDeclaringClass()
                + ".  This breaks the JavaBeans " + "specification and can cause unpredictable results.");
          } else if (methodType.isAssignableFrom(getterType)) {
            // 如果当前方法返回值是第一个方法返回值的父类，那么第一个方法还是最合适的，什么也不做
          } else if (getterType.isAssignableFrom(methodType)) {
            // 如果当前方法返回值是第一个方法返回值的子类，则当前方法是最合适的
            getter = method;
            getterType = methodType;
          } else {
            // 如果还有其他情况直接抛出异常
            throw new ReflectionException("Illegal overloaded getter method with ambiguous type for property "
                + propName + " in class " + firstMethod.getDeclaringClass()
                + ".  This breaks the JavaBeans " + "specification and can cause unpredictable results.");
          }
        }
        //添加到getMethods、getTypes字段中
        addGetMethod(propName, getter);
      }
    }
  }

  private void addGetMethod(String name, Method method) {
    //校验属性名是否合法
    if (isValidPropertyName(name)) {
      //填充getMethods和getTypes集合
      getMethods.put(name, new MethodInvoker(method));
      //解析方法返回值类型加入到getTypes中，TypeParameterResolver将在下篇文章中详细讲解
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      getTypes.put(name, typeToClass(returnType));
    }
  }

  private void addSetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          name = PropertyNamer.methodToProperty(name);
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    List<Method> list = conflictingMethods.get(name);
    if (list == null) {
      list = new ArrayList<Method>();
      conflictingMethods.put(name, list);
    }
    list.add(method);
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      for (Method setter : setters) {
        Class<?> paramType = setter.getParameterTypes()[0];
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance((Class<?>) componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    //获取该类声明的字段
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (canAccessPrivateMethods()) {
        try {
          field.setAccessible(true);
        } catch (Exception e) {
          // Ignored. This is only a final precaution, nothing we can do.
        }
      }
      if (field.isAccessible()) {
        //如果setMethods集合中不包含同名属性，也就是该字段没有setter方法
        if (!setMethods.containsKey(field.getName())) {
          // 过滤掉final和static修饰的字段
          int modifiers = field.getModifiers();
          if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
            //添加到setMethods、setTypes集合中
            addSetField(field);
          }
        }
        //如果getMethods集合中不包含同名属性，也就是该字段没有getter方法
        if (!getMethods.containsKey(field.getName())) {
          //添加到getMethods、getTypes集合中
          addGetField(field);
        }
      }
    }
    //继续查找父类
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /*
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler Class.getMethods(),
   * because we want to look for private methods as well.
   *
   * @param cls The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> cls) {
    Map<String, Method> uniqueMethods = new HashMap<String, Method>();
    Class<?> currentClass = cls;
    while (currentClass != null) {
      //为每个方法生成唯一签名，记录到uniqueMethods集合中
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      /*
      * 记录接口中定义的方法，因为这个类可能是一个抽象类或者接口
      * */
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      //继续查找父类方法
      currentClass = currentClass.getSuperclass();
    }

    //将结果返回
    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[methods.size()]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      //如果方法不是桥接方法
      if (!currentMethod.isBridge()) {
        /*
        * 为方法生成签名，签名规则为  返回值类型#方法名称:参数类型列表1.参数类型列表2
        * 如 public String User.getUserId(String userId, Integer userId1)的方法签名为
        * java.lang.String#getSignature:java.lang.String,java.lang.Integer
        * */
        String signature = getSignature(currentMethod);
        /*
        * 检测是否已经在子类添加过此方法了，如果已经添加过则不再添加
        * */
        if (!uniqueMethods.containsKey(signature)) {
          if (canAccessPrivateMethods()) {
            try {
              currentMethod.setAccessible(true);
            } catch (Exception e) {
              // Ignored. This is only a final precaution, nothing we can do.
            }
          }

          //返回结果
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    //如果有返回值则拼接  返回值#
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    //拼接方法名称
    sb.append(method.getName());
    //拼接方法的参数类型
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      //如果是第一个则拼接”:参数类型“  非首个则拼接 “,参数类型”
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    //返回结果
    return sb.toString();
  }

  private static boolean canAccessPrivateMethods() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /*
   * Gets the name of the class the instance provides information for
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /*
   * Gets the type for a property setter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets the type for a property getter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets an array of the readable properties for an object
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /*
   * Gets an array of the writeable properties for an object
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writeablePropertyNames;
  }

  /*
   * Check to see if a class has a writeable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writeable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /*
   * Check to see if a class has a readable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
