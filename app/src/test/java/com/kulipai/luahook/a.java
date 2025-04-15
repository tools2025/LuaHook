package com.kulipai.luahook;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class a {


    public void main() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        // 获取 File 类的 Class 对象
        Class<?> fileClass = Class.forName("java.io.File");

        // 获取接受 String 参数的构造方法
        Constructor<?> constructor = fileClass.getDeclaredConstructor(String.class);

        // 创建 File 对象的实例
        String filePath = "path/to/your/reflected_file.txt";
        File reflectedFile = (File) constructor.newInstance(filePath);

        System.out.println("Reflected File: " + reflectedFile.getAbsolutePath());

    }
}
