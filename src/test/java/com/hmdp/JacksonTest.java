package com.hmdp;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author fzy
 * @version 1.0
 * 创建时间：2025-07-10 14:25
 */

public class JacksonTest {

    @Data
    public static class Person{
        String name;
        Animal pet;
    }

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Dog.class, name = "dog"),
            @JsonSubTypes.Type(value = Cat.class, name = "cat")
    })
    public static interface Animal{
    }

    @Data
    public static class Dog implements Animal{
        String breed;
    }

    @Data static class Cat implements Animal{
        String color;
    }

    @Test
    public void test() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Person person = new Person();
        person.name = "fzy";

        Animal dog = new Dog();
        ((Dog) dog).setBreed("Golden Retriever");
        Animal cat = new Cat();
        ((Cat) cat).setColor("Black");

        person.pet = dog; // 可以切换为 cat 测试
        String json = objectMapper.writeValueAsString(person);
        System.out.println("Serialized JSON: " + json);
        // 反序列化
        Person deserializedPerson = objectMapper.readValue(json, Person.class);
        System.out.println("Deserialized Person: " + deserializedPerson);
        System.out.println("Pet Type: " + deserializedPerson.getPet().getClass().getSimpleName());

    }
}
