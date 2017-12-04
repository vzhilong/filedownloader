package cn.icheny.download;

import java.util.Collection;

/**
 * Created by vincent on 2017/12/3.
 */

public class CollectionUtils {
    public static int size(Collection col) {
        if (col == null) {
            return 0;
        }

        return col.size();
    }

    public static boolean isEmpty(Collection col) {
        return size(col) == 0;
    }

    public static boolean isNotEmpty(Collection col) {
        return size(col) != 0;
    }
}
