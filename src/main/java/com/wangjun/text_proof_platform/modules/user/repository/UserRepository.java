package com.wangjun.text_proof_platform.modules.user.repository;

import com.wangjun.text_proof_platform.modules.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

//Repository 是访问数据库的组件，主要负责对 Entity 做增删改查。

// 继承 JpaRepository<实体类型, ID类型>，Spring 会自动帮你实现 save, findById 等方法
public interface UserRepository extends JpaRepository<User,Long> {
    //方法名派生查询 / 根据方法名自动生成查询
    //按照规则命名方法，Spring 就能自动生成 SQL：select * from user where username = ?
    //Optional<User>返回结果可能为空,如果不用Optional的话，返回可能报错
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    //JPQL:方法名本身不能自动表达“加锁更新”的含义，所以需要配合 @Query 自定义查询语句，再用 @Lock 指定加锁方式。
    //因为 @Query 默认写的是 JPQL，它面向的是 实体类和实体字段，不是数据库表和列；
    //所以这里写 User，而不是 sys_user。
    @Query("select u from User u where u.username= :username")
    //因为 @Query 里写的是命名参数,这个 :username 到底对应方法里的哪一个参数,@Param("username")就是显式告诉它：对应这个名字
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email= :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);


}
