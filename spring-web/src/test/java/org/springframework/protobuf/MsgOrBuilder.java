// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: sample.proto

package org.springframework.protobuf;

public interface MsgOrBuilder extends com.google.protobuf.MessageOrBuilder {

  // optional string foo = 1;
  /** <code>optional string foo = 1;</code> */
  boolean hasFoo();
  /** <code>optional string foo = 1;</code> */
  java.lang.String getFoo();
  /** <code>optional string foo = 1;</code> */
  com.google.protobuf.ByteString getFooBytes();

  // optional .SecondMsg blah = 2;
  /** <code>optional .SecondMsg blah = 2;</code> */
  boolean hasBlah();
  /** <code>optional .SecondMsg blah = 2;</code> */
  org.springframework.protobuf.SecondMsg getBlah();
  /** <code>optional .SecondMsg blah = 2;</code> */
  org.springframework.protobuf.SecondMsgOrBuilder getBlahOrBuilder();
}
