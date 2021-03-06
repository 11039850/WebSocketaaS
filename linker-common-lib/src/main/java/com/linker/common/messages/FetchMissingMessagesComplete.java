package com.linker.common.messages;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class FetchMissingMessagesComplete implements Serializable {

    private static final long serialVersionUID = 3125923390100977950L;
    Long leftMissingCount = 0L;
}
