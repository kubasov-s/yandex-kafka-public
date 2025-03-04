package com.example.module2.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class Message {
    private String message;
    private String fromUser;
}
