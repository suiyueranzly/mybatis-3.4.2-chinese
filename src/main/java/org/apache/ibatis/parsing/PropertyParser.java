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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class PropertyParser {

  /**
   * 配置项的前缀
   * */
  private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
  /**
   * 是否启动默认值功能的配置key
   *
   * @since 3.4.2
   */
  public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

  /**
   * 分隔符的配置key
   *
   * @since 3.4.2
   */
  public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

  /**
   * 默认不开启默认值功能
   *
   */
  private static final String ENABLE_DEFAULT_VALUE = "false";

  /**
   * 默认分隔符为":"
   *
   */
  private static final String DEFAULT_VALUE_SEPARATOR = ":";

  private PropertyParser() {
    // Prevent Instantiation
  }

  /**
   * 根据分隔符和默认值功能解析字符串
   * */
  public static String parse(String string, Properties variables) {
    VariableTokenHandler handler = new VariableTokenHandler(variables);
    GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
    return parser.parse(string);
  }

  private static class VariableTokenHandler implements TokenHandler {
    /**
     * properties标签中配置的键值对
     * */
    private final Properties variables;
    /**
     * 是否开启默认值功能
     * */
    private final boolean enableDefaultValue;
    /**
     * 默认分隔符
     * */
    private final String defaultValueSeparator;

    private VariableTokenHandler(Properties variables) {
      this.variables = variables;
      this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
      this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
    }

    private String getPropertyValue(String key, String defaultValue) {
      return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
    }

    @Override
    public String handleToken(String content) {
      if (variables != null) {
        String key = content;
        //如果开启了默认值功能
        if (enableDefaultValue) {
          //查找是否有分隔符
          final int separatorIndex = content.indexOf(defaultValueSeparator);
          String defaultValue = null;
          if (separatorIndex >= 0) {
            /*
            * 如果有分隔符，则分割key和后面的默认值，如  ${username:abc}
            * 这里的key就是username，默认值就是abc
            * */
            key = content.substring(0, separatorIndex);
            defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
          }
          if (defaultValue != null) {
            //如果找到就返回，找不到就返回默认值
            return variables.getProperty(key, defaultValue);
          }
        }
        //如果properties中配置的键值对中存在该key，则从中获取
        if (variables.containsKey(key)) {
          return variables.getProperty(key);
        }
      }
      //如果properties没有配置键值对的话，这里就直接返回
      return "${" + content + "}";
    }
  }

}
