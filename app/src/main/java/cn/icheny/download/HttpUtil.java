package cn.icheny.download;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


/**
 * Http网络工具,基于OkHttp
 * Created by Cheny on 2017/04/29.
 */

public class HttpUtil {
    private static final long CONNECT_TIMEOUT = 60;//超时时间，秒
    private static final long READ_TIMEOUT = 60;//读取时间，秒
    private static final long WRITE_TIMEOUT = 60;//写入时间，秒

    private static OkHttpClient sHttpClient;

    static {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS);
        sHttpClient = builder.build();
    }


    /**
     * @param url        下载链接
     * @param startIndex 下载起始位置
     * @param endIndex   结束为止
     * @param callback   回调
     * @throws IOException
     */
    public static void downloadFileByRange(String url, long startIndex, long endIndex, Callback callback) throws IOException {
        // 创建一个Request
        // 设置分段下载的头信息。 Range:做分段数据请求,断点续传指示下载的区间。格式: Range bytes=0-1024或者bytes:0-1024
        Request request = new Request.Builder().header("RANGE", "bytes=" + startIndex + "-" + endIndex)
                .url(url)
                .build();
        doAsync(request, callback);
    }

    public static void getContentLength(String url, Callback callback) throws IOException {
        // 创建一个Request
        Request request = new Request.Builder()
                .url(url)
                .build();
        doAsync(request, callback);
    }

    /**
     * 异步请求
     */
    private static void doAsync(Request request, Callback callback) throws IOException {
        //创建请求会话
        Call call = sHttpClient.newCall(request);
        //同步执行会话请求
        call.enqueue(callback);
    }

    /**
     * 同步请求
     */
    private static Response doSync(Request request) throws IOException {

        //创建请求会话
        Call call = sHttpClient.newCall(request);
        //同步执行会话请求
        return call.execute();
    }
}
