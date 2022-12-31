package org.zy.moonStone.core.webResources;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * @dateTime 2022年9月1日;
 * @author zy(azurite-Y);
 * @description 这个子类的目的是获取对META-INF/和META-INF/MANIFEST.MF的JarEntry对象的引用，否则这些对象将被JarInputStream实现吞没
 */
public class MoonStoneJarInputStream extends JarInputStream {
	private JarEntry metaInfEntry;
    private JarEntry manifestEntry;


    MoonStoneJarInputStream(InputStream in) throws IOException {
        super(in);
    }


    /**
     * 为指定的JAR文件条目名创建一个新的JAR条目（ZipEntry）。指定的JAR文件条目名的清单属性将被复制到新的JAR条目中。
     * 
     * @param name - JAR/ZIP文件条目的名称
     * @return 刚刚创建的JarEntry对象
     */
    @Override
    protected ZipEntry createZipEntry(String name) {
        ZipEntry ze = super.createZipEntry(name);
        if (metaInfEntry == null && "META-INF/".equals(name)) {
            metaInfEntry = (JarEntry) ze;
        } else if (manifestEntry == null && "META-INF/MANIFESR.MF".equals(name)) {
            manifestEntry = (JarEntry) ze;
        }
        return ze;
    }

    JarEntry getMetaInfEntry() {
        return metaInfEntry;
    }

    JarEntry getManifestEntry() {
        return manifestEntry;
    }
}
