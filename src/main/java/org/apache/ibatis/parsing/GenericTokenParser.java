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

/**
 * @author Clinton Begin
 */
public class GenericTokenParser {

  /**
   * 占位符的开始字符
   * */
  private final String openToken;
  /**
   * 占位符的结束字符
   * */
  private final String closeToken;
  /**
   * 占位符内的表达式处理器
   * */
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    //如果为空直接返回
    if (text == null || text.isEmpty()) {
      return "";
    }
    char[] src = text.toCharArray();
    int offset = 0;
    // 查找开始标记的位置
    int start = text.indexOf(openToken, offset);
    //如果不存在则直接返回
    if (start == -1) {
      return text;
    }
    //builder用来保存解析后的字符串
    final StringBuilder builder = new StringBuilder();
    //expression用来保存占位符内的公式
    StringBuilder expression = null;
    while (start > -1) {
      /*
      * 如果开始字符前面是转义字符“\”，则去掉转义字符
      * 比如\${abc，会拼接成${abc
      * */
      if (start > 0 && src[start - 1] == '\\') {
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        // 重置expression对象
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        /*
         * 首先拼接offset到开始字符之间的普通字符串（此时的offset为0）
         * 如test${id_var}，则先将test拼接到结果中
         * */
        builder.append(src, offset, start - offset);
        //将offset更改为开始字符后面的的位置
        offset = start + openToken.length();
        //从offset的位置开始查找结束标记的位置
        int end = text.indexOf(closeToken, offset);
        //如果找到了结束标记
        while (end > -1) {
          //处理结束标记的转移字符
          if (end > offset && src[end - 1] == '\\') {
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            /*
             * 将开始标记和结束标记之间的字符拼接到表达式中
             * 如${abc}，这里会拼接abc
             * */
            expression.append(src, offset, end - offset);
            //重新计算offset
            offset = end + closeToken.length();
            break;
          }
        }
        if (end == -1) {
          // 如果找不到关闭字符，则将开始字符后面的文本拼接起来
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          //将占位符里面的表达式文本交给TokenHandler处理并拼接到结果中
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      //再次开始查找
      start = text.indexOf(openToken, offset);
    }
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
