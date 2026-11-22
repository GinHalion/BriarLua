
package com.zzzmode.apkeditor.utils;

public interface BinaryDecoder extends Decoder {

    byte[] decode(byte[] source) throws DecoderException;
}
