package cn.icheny.download;

import java.io.File;

/**
 * Created by Cheny on 2017/4/29.
 */

public class FilePoint {
    private String url;//下载地址
    private String fileDir;//下载目录
    private String fileName;//文件名
    private String fileExt; //

    public FilePoint(String url, String fileDir, String fileName, String fileExt) {
        this.url = url;
        this.fileDir = fileDir;
        this.fileName = fileName;
        this.fileExt = fileExt;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileDir() {
        return fileDir;
    }

    public String getFileExt() {
        return fileExt;
    }

    public String getOutFilePath() {
        if (fileExt != null) {
            return new File(fileDir, fileName + "." + fileExt).getPath();
        } else {
            return new File(fileDir, fileName).getPath();
        }
    }

    public String getTmpFilePath() {
        return new File(fileDir, fileName + ".tmp").getPath();
    }

}
