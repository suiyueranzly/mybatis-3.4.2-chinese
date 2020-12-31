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
package org.apache.ibatis.builder.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.apache.ibatis.io.Resources;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Offline entity resolver for the MyBatis DTDs
 * 
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class XMLMapperEntityResolver implements EntityResolver {

  /***
   * ibatis-config的dtd文件名称
   * **/
  private static final String IBATIS_CONFIG_SYSTEM = "ibatis-3-config.dtd";
  /***
   * ibatis mapper文件的dtd文件名称
   * **/
  private static final String IBATIS_MAPPER_SYSTEM = "ibatis-3-mapper.dtd";
  /***
   * mybatis-config的dtd文件名称
   * **/
  private static final String MYBATIS_CONFIG_SYSTEM = "mybatis-3-config.dtd";
  /***
   * mybatis mapper文件的dtd文件名称
   * **/
  private static final String MYBATIS_MAPPER_SYSTEM = "mybatis-3-mapper.dtd";

  private static final String MYBATIS_CONFIG_DTD = "org/apache/ibatis/builder/xml/mybatis-3-config.dtd";
  private static final String MYBATIS_MAPPER_DTD = "org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd";

  /**
   * publicId：dtd文件的网络位置，如http://mybatis.org/dtd/mybatis-3-config.dtd
   * 根据对应的systemId（如mybatis-3-mapper.dtd）找到对应的dtd文件
   * 并封装成inputSource文件返回，此类允许SAX应用程序在单个对象中封装有关输入源的信息
   */
  @Override
  public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
    try {
      if (systemId != null) {
        //转换为小写
        String lowerCaseSystemId = systemId.toLowerCase(Locale.ENGLISH);
        //根据不同的systemId找到对应的文件
        if (lowerCaseSystemId.contains(MYBATIS_CONFIG_SYSTEM) || lowerCaseSystemId.contains(IBATIS_CONFIG_SYSTEM)) {
          return getInputSource(MYBATIS_CONFIG_DTD, publicId, systemId);
        } else if (lowerCaseSystemId.contains(MYBATIS_MAPPER_SYSTEM) || lowerCaseSystemId.contains(IBATIS_MAPPER_SYSTEM)) {
          return getInputSource(MYBATIS_MAPPER_DTD, publicId, systemId);
        }
      }
      return null;
    } catch (Exception e) {
      throw new SAXException(e.toString());
    }
  }

  /**
   * 根据路径找到对应的dtd本地文件并返回
   * **/
  private InputSource getInputSource(String path, String publicId, String systemId) {
    InputSource source = null;
    if (path != null) {
      try {
        InputStream in = Resources.getResourceAsStream(path);
        source = new InputSource(in);
        source.setPublicId(publicId);
        source.setSystemId(systemId);        
      } catch (IOException e) {
        // 忽略，找不到就直接返回null
      }
    }
    return source;
  }

}