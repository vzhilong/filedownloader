package cn.icheny.download;

import java.util.HashMap;
import java.util.Map;

/**
 * 下载管理器，断点续传
 *
 * @author Cheny
 */
public class DownloadManager {

    private static final String TAG = "DownloadManager";
    private static DownloadManager mInstance;
    private String defaultDir;
    private Map<String, DownloadTask> mDownloadTasks;//文件下载任务索引，String为url,用来唯一区别并操作下载的文件

    public DownloadManager() {
        mDownloadTasks = new HashMap<>();
    }


    public static DownloadManager getInstance() {//管理器初始化
        if (mInstance == null) {
            synchronized (DownloadManager.class) {
                if (mInstance == null) {
                    mInstance = new DownloadManager();
                }
            }
        }
        return mInstance;
    }

    public void initDefaultDir(String path) {
        defaultDir = path;
    }


    /**
     * 下载文件
     */
    public void download(String... urls) {
        //单任务开启下载或多任务开启下载
        for (int i = 0, length = urls.length; i < length; i++) {
            String url = urls[i];
            if (mDownloadTasks.containsKey(url)) {
                mDownloadTasks.get(url).start();
            }
        }
    }

    /**
     * 暂停
     */
    public void pause(String... urls) {
        //单任务暂停或多任务暂停下载
        for (int i = 0, length = urls.length; i < length; i++) {
            String url = urls[i];
            if (mDownloadTasks.containsKey(url)) {
                mDownloadTasks.get(url).pause();
            }
        }
    }

    /**
     * 取消下载
     */
    public void cancel(String... urls) {
        //单任务取消或多任务取消下载
        for (int i = 0, length = urls.length; i < length; i++) {
            String url = urls[i];
            if (mDownloadTasks.containsKey(url)) {
                mDownloadTasks.get(url).cancel();
            }
        }
    }

    /**
     * 添加下载任务
     */
    public void add(String url, DownloadListener listener) {
        add(url, defaultDir, MD5.encrypt(url), listener);
    }

    /**
     * 添加下载任务
     */
    public void add(String url, String filePath, String fileName, DownloadListener listener) {
        mDownloadTasks.put(url, new DownloadTask(new FilePoint(url, filePath, fileName, "apk"), listener));
    }


    /**
     * 是否在下载
     *
     * @param url
     * @return
     */
    public boolean isDownloading(String url) {
        boolean result = false;
        if (mDownloadTasks.containsKey(url)) {
            result = mDownloadTasks.get(url).isDownloading();
        }

        return result;
    }
}
