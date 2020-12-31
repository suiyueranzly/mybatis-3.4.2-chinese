package org.apache.ibatis.test.dao;

import org.apache.ibatis.test.entity.Users;

public interface UsersDao {

    Users selectByPrimaryKey(Integer id);
}