package cn.icheny.download;

/**
 * Created by Cheny on 2017/4/29.
 */

public class FilePoint {
    private String url;//下载地址
    private String filePath;//下载目录
    private String fileName;//文件名
    private String fileExt; //

    public FilePoint(String url, String filePath, String fileName, String fileExt) {
        this.url = url;
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileExt = fileExt;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileExt() {
        return fileExt;
    }

}
