package util;

import core.Coord;

import java.util.List;

public class PathDistance {

    private static final double nearby = 100;

    public static double lcs(List<Coord> l1, List<Coord> l2) {
        int len1 = l1.size();
        int len2 = l2.size();
        var c = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            for (int j = 0; j <= len2; j++) {
                if (i == 0 || j == 0) {
                    c[i][j] = 0;
                } else if (isNearby(l1.get(i - 1), l2.get(j - 1))) {
                    c[i][j] = c[i - 1][j - 1] + 1;
                } else {
                    c[i][j] = Math.max(c[i][j - 1], c[i - 1][j]);
                }
            }
        }
        return (double) c[len1][len2] / Math.min(len1, len2);
    }

    private static boolean isNearby(Coord c1, Coord c2) {
        return (c1.distance(c2) < nearby);
    }
}
