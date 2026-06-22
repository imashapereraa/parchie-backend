package com.parchie.bdd;

public final class HexBytes {

    private HexBytes() {}

    public static byte[] parse(String csv) {
        String[] parts = csv.split(",");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i].trim();
            if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
            out[i] = (byte) Integer.parseInt(s, 16);
        }
        return out;
    }
}
