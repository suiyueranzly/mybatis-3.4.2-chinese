/**
 * All rights Reserved, Designed By www.rongdasoft.com
 *
 * @version V1.0
 * @Title: Test.java
 * @author: zhangyq
 * @date: 2020/12/29
 * @Copyright: 2020/12/29 www.rongdasoft.com Inc. All rights reserved.
 */
package org.apache.ibatis.test;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.test.dao.UsersDao;
import org.apache.ibatis.test.entity.Users;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName: Test
 * @author zhangyq
 * @date 2020/12/29
 */
public class Test {
    public static void main(String[] args) throws IOException {
        String resource = "mybatis-config.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        try (SqlSession session = sqlSessionFactory.openSession()) {
            UsersDao usersDao = session.getMapper(UsersDao.class);
            Users users = usersDao.selectByPrimaryKey(1);
            System.out.println(users);
        }
    }
}
