package com.ysh.tools;

public class CommonUtils {

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.isEmpty();
    }


    public static String capitalize(String str) {
        if (isEmpty(str)) {
            return str;
        } else {
            int firstCodepoint = str.codePointAt(0);
            int newCodePoint = Character.toTitleCase(firstCodepoint);
            if (firstCodepoint == newCodePoint) {
                return str;
            } else {
                int[] newCodePoints = str.codePoints().toArray();
                newCodePoints[0] = newCodePoint;
                return new String(newCodePoints, 0, newCodePoints.length);
            }
        }
    }
}
