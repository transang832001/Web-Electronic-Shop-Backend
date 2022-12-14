package com.example.electronicshop.repository;


import com.example.electronicshop.communication.StateCountAggregate;
import com.example.electronicshop.models.enity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User,String> {
    Optional<User> findUserByEmailAndState(String email, String state);
    Optional<User> findUsersByEmail(String email);
    Optional<User> findUserByIdAndState(String id, String state);
    Page<User> findUsersByRole(String role, Pageable pageable);

//    @Query("{'email': ?0, 'state': ?1}")
//    Optional<User> findEmailAndState(String email, String state);
    boolean existsByEmail(String email);

//    Optional<User> findUserById(String id);
    @Aggregation("{ $group: { _id : $state, count: { $sum: 1 } } }")
    List<StateCountAggregate> countAllByState();
}
