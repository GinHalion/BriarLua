
package com.zzzmode.apkeditor.utils;

public interface BinaryEncoder extends Encoder {

    byte[] encode(byte[] source) throws EncoderException;
}
