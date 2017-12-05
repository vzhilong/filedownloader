package cn.icheny.download;

/**
 * 下载监听
 *
 * @author Cheny
 */
public interface DownloadListener {

    void onProgress(String url, long sofar, long total);

    void onFinished(String url, String filePath);

    void onPause(String url);

    void onDelete(String url);

    void onError(String url, int errorType);
}
