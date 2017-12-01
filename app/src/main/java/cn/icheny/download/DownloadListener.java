package cn.icheny.download;

/**
 * 下载监听
 *
 * @author Cheny
 */
public interface DownloadListener {
    void onFinished(String url, String filePath);

    void onProgress(String url, long sofar, long total);

    void onPause(String url);

    void onCancel(String url);

    void onError(int errorType);
}
