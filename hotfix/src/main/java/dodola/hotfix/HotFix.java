/*
 * Copyright (C) 2015 Baidu, Inc. All Rights Reserved.
 */
package dodola.hotfix;

import android.annotation.TargetApi;
import android.content.Context;
import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/* compiled from: ProGuard */


/**
 * https://github.com/dodola/HotFix/blob/master/hotfixlib/src/main/java/dodola/hotfixlib/HotFix.java
 */
public final class HotFix {

    private HotFix() {}


    public static void patch(Context context, String patchDexFile, String patchClassName) {
        if (patchDexFile != null && new File(patchDexFile).exists()) {
            try {
                if (hasLexClassLoader()) {
                    injectInAliyunOs(context, patchDexFile, patchClassName);
                } else if (hasDexClassLoader()) {
                    injectAboveEqualApiLevel14(context, patchDexFile, patchClassName);
                } else {
                    injectBelowApiLevel14(context, patchDexFile, patchClassName);
                }
            } catch (Throwable th) {
            }
        }
    }


    /**
     * 判断是否是 阿里云 OS 机型
     */
    private static boolean hasLexClassLoader() {
        try {
            Class.forName("dalvik.system.LexClassLoader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    /**
     * 判断是否是 API 14 以上
     */
    private static boolean hasDexClassLoader() {
        try {
            Class.forName("dalvik.system.BaseDexClassLoader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    /**
     * 阿里云 OS 上的 插桩策略
     *
     * @param context context
     * @param patchDexFile 插件 dex 的地址
     * @param patchClassName 插件 dex 中要修复的类 name
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     * @throws NoSuchFieldException
     */
    private static void injectInAliyunOs(Context context, String patchDexFile, String patchClassName)
        throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
               InvocationTargetException,
               InstantiationException, NoSuchFieldException {
        PathClassLoader obj = (PathClassLoader) context.getClassLoader();
        String replaceAll = new File(patchDexFile).getName().replaceAll("\\.[a-zA-Z0-9]+", ".lex");
        Class cls = Class.forName("dalvik.system.LexClassLoader");
        Object newInstance =
            cls.getConstructor(
                new Class[] { String.class, String.class, String.class, ClassLoader.class })
                .newInstance(
                    new Object[] {
                        context.getDir("dex", 0).getAbsolutePath() + File.separator + replaceAll,
                        context.getDir("dex", 0).getAbsolutePath(), patchDexFile, obj });
        cls.getMethod("loadClass", new Class[] { String.class })
            .invoke(newInstance, new Object[] { patchClassName });
        setField(obj, PathClassLoader.class, "mPaths",
            appendArray(getField(obj, PathClassLoader.class, "mPaths"),
                getField(newInstance, cls, "mRawDexPath")));
        setField(obj, PathClassLoader.class, "mFiles",
            combineArray(getField(obj, PathClassLoader.class, "mFiles"),
                getField(newInstance, cls, "mFiles")));
        setField(obj, PathClassLoader.class, "mZips",
            combineArray(getField(obj, PathClassLoader.class, "mZips"),
                getField(newInstance, cls, "mZips")));
        setField(obj, PathClassLoader.class, "mLexs",
            combineArray(getField(obj, PathClassLoader.class, "mLexs"),
                getField(newInstance, cls, "mDexs")));
    }


    /**
     * 小于 API 14 的插桩策略
     *
     * @param context context
     * @param str 插件 dex 的路径
     * @param str2 插件 dex 中要修复的类 name
     * @throws ClassNotFoundException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    @TargetApi(14)
    private static void injectBelowApiLevel14(Context context, String str, String str2)
        throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        PathClassLoader obj = (PathClassLoader) context.getClassLoader();
        DexClassLoader dexClassLoader =
            new DexClassLoader(str, context.getDir("dex", 0).getAbsolutePath(), str,
                context.getClassLoader());
        dexClassLoader.loadClass(str2);
        setField(obj, PathClassLoader.class, "mPaths",
            appendArray(getField(obj, PathClassLoader.class, "mPaths"),
                getField(dexClassLoader, DexClassLoader.class,
                    "mRawDexPath")
            ));
        setField(obj, PathClassLoader.class, "mFiles",
            combineArray(getField(obj, PathClassLoader.class, "mFiles"),
                getField(dexClassLoader, DexClassLoader.class,
                    "mFiles")
            ));
        setField(obj, PathClassLoader.class, "mZips",
            combineArray(getField(obj, PathClassLoader.class, "mZips"),
                getField(dexClassLoader, DexClassLoader.class,
                    "mZips")));
        setField(obj, PathClassLoader.class, "mDexs",
            combineArray(getField(obj, PathClassLoader.class, "mDexs"),
                getField(dexClassLoader, DexClassLoader.class,
                    "mDexs")));
        obj.loadClass(str2);
    }


    /**
     * 大于 API 14 的插桩策略
     *
     * @param context context
     * @param str dex 的路径
     * @param str2 插件 dex 中要修复的类 name
     * @throws ClassNotFoundException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private static void injectAboveEqualApiLevel14(Context context, String str, String str2)
        throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        PathClassLoader pathClassLoader = (PathClassLoader) context.getClassLoader();
        Object a = combineArray(getDexElements(getPathList(pathClassLoader)),
            getDexElements(getPathList(
                new DexClassLoader(str, context.getDir("dex", 0).getAbsolutePath(), str,
                    context.getClassLoader()))));
        Object a2 = getPathList(pathClassLoader);
        setField(a2, a2.getClass(), "dexElements", a);
        pathClassLoader.loadClass(str2);
    }


    /**
     * 获取 pathList
     *
     * @param obj 对应的类
     * @return pathList
     * @throws ClassNotFoundException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private static Object getPathList(Object obj)
        throws ClassNotFoundException, NoSuchFieldException,
               IllegalAccessException {
        return getField(obj, Class.forName("dalvik.system.BaseDexClassLoader"), "pathList");
    }


    /**
     * 获取 dexElements[]
     *
     * @param obj 对应 类
     * @return dexElements[]
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    private static Object getDexElements(Object obj)
        throws NoSuchFieldException, IllegalAccessException {
        return getField(obj, obj.getClass(), "dexElements");
    }


    private static Object getField(Object obj, Class cls, String str)
        throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = cls.getDeclaredField(str);
        declaredField.setAccessible(true);
        return declaredField.get(obj);
    }


    private static void setField(Object obj, Class cls, String str, Object obj2)
        throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = cls.getDeclaredField(str);
        declaredField.setAccessible(true);
        declaredField.set(obj, obj2);
    }


    /**
     * 合并两个数组
     *
     * @param obj 数组
     * @param obj2 数组
     * @return 合并后数组
     */
    private static Object combineArray(Object obj, Object obj2) {
        Class componentType = obj2.getClass().getComponentType();
        int length = Array.getLength(obj2);
        int length2 = Array.getLength(obj) + length;
        Object newInstance = Array.newInstance(componentType, length2);
        for (int i = 0; i < length2; i++) {
            if (i < length) {
                Array.set(newInstance, i, Array.get(obj2, i));
            } else {
                Array.set(newInstance, i, Array.get(obj, i - length));
            }
        }
        return newInstance;
    }


    /**
     * 添加子元素到数组
     *
     * @param obj 数组
     * @param obj2 子元素
     * @return 添加后的数组
     */
    private static Object appendArray(Object obj, Object obj2) {
        Class componentType = obj.getClass().getComponentType();
        int length = Array.getLength(obj);
        Object newInstance = Array.newInstance(componentType, length + 1);
        Array.set(newInstance, 0, obj2);
        for (int i = 1; i < length + 1; i++) {
            Array.set(newInstance, i, Array.get(obj, i - 1));
        }
        return newInstance;
    }
}