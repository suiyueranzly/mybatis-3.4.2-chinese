/**
 * All rights Reserved, Designed By www.rongdasoft.com
 *
 * @version V1.0
 * @Title: XmlParseTest.java
 * @author: zhangyq
 * @date: 2020/12/29
 * @Copyright: 2020/12/29 www.rongdasoft.com Inc. All rights reserved.
 */
package org.apache.ibatis.test;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;

/**
 * @ClassName: XmlParseTest
 * @author zhangyq
 * @date 2020/12/29
 */
public class XmlParseTest {
    public static void main(String[] args) throws ParserConfigurationException, IOException,
            SAXException, XPathExpressionException {
        //首先创建文档解析器工厂
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                .newInstance();

        //校验文档(DTD验证)
        documentBuilderFactory.setValidating(true);
        //提供对XML命名空间的支持
        documentBuilderFactory.setNamespaceAware(false);
        //忽略备注
        documentBuilderFactory.setIgnoringComments(true);
        //忽略空格
        documentBuilderFactory.setIgnoringElementContentWhitespace(false);
        //把CDATA节点转换为text节点，并将其附加到相邻的文本节点（如果有）
        documentBuilderFactory.setCoalescing(false);
        //展开实体引用节点
        documentBuilderFactory.setExpandEntityReferences(true);

        //创建文档解析器
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

        //设置解析器的错误处理
        documentBuilder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                System.out.println("warning:" + exception.getMessage());
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                System.out.println("error:" + exception.getMessage());
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                System.out.println("fatalError:" + exception.getMessage());
            }
        });


        //加载文档
        Document parse = documentBuilder.parse("src\\main\\resources\\inventory.xml");

        //创建xpath工厂
        XPathFactory factory = XPathFactory.newInstance();

        XPath xPath = factory.newXPath();

        //编译xpath表达式
        XPathExpression compile = xPath.compile("//car[color='black']/name/text()");

        //通过xpath表达式获得结果
        Object evaluate = compile.evaluate(parse, XPathConstants.NODESET);

        System.out.println("查询颜色是黑色的汽车");

        printNodeValue(parse, compile);

        System.out.println("查询价格小于于19000的汽车");

        compile = xPath.compile("//car[price < 19000]/name/text()");

        printNodeValue(parse, compile);

        System.out.println("查询1999年后所有汽车的属性和价格");

        compile = xPath.compile("//car[@year > 1995]/@*|//car[@year > 1995]/price/text()");

        printNodeValue(parse, compile);
    }

    /***
     * 打印节点值
     * **/
    private static void printNodeValue(Document parse, XPathExpression compile) throws XPathExpressionException {
        Object evaluate = compile.evaluate(parse, XPathConstants.NODESET);

        NodeList nodeList = (NodeList) evaluate;
        for (int i = 0; i < nodeList.getLength(); i++) {
            System.out.println(nodeList.item(i).getNodeValue());
        }
    }
}
