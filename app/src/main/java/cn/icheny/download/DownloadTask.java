package cn.icheny.download;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Cheny on 2017/4/29.
 */

public class DownloadTask {

    private static final String TAG = "DownloadTask";
    private static OkHttpClient sHttpClient;

    static {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS);
        sHttpClient = builder.build();
    }

    private final int THREAD_COUNT = 4;//线程数
    private final int RETRY_TIMES = 10;

    private final int STATUS_READY = 0;
    private final int STATUS_DOWNLOADING = 1;
    private final int STATUS_FINISHED = 2;
    private final int STATUS_PAUSE = 3;
    private final int STATUS_ERROR = 4;


    private int status = STATUS_READY;
    private List<Call> mAllCaller;
    private FilePoint mPoint;
    private AtomicLong mSavedSize;

    private long mFileLength;
    private File mTmpFile;//临时占位文件
    private File mOutFile;
    private DownloadListener mListener;//下载回调监听
    private Handler mMainHandler;
    private boolean mStopRead = false;
    private SparseIntArray mRetryTimes = new SparseIntArray();

    /**
     * 任务管理器初始化数据
     *
     * @param point
     * @param listener
     */
    DownloadTask(FilePoint point, DownloadListener listener) {
        this.mPoint = point;
        this.mListener = listener;

        mTmpFile = new File(mPoint.getFilePath(), mPoint.getFileName() + ".tmp");
        if (mPoint.getFileExt() == null) {
            mOutFile = new File(mPoint.getFilePath(), mPoint.getFileName());
        } else {
            mOutFile = new File(mPoint.getFilePath(), mPoint.getFileName() + "." + mPoint.getFileExt());
        }
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public static boolean isFinished(String url) {
        String recordKey = MD5.encrypt(url);
        DownloadRecord downloadRecord = DownloadDBHelper.getInstance().getRecord(recordKey);
        if (downloadRecord != null) {
            File file = new File(downloadRecord.filePath);
            if (file.exists() && downloadRecord.fileLength == file.length()) {
                return true;
            }
        }

        return false;
    }

    public synchronized void start() {

        if (status == STATUS_DOWNLOADING) {
            return;
        }
        status = STATUS_DOWNLOADING;
        mSavedSize = new AtomicLong(0);
        mStopRead = false;
        mAllCaller = new ArrayList<>();
        mRetryTimes.clear();

        Callback requestCallback = new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                int responseCode = response.code();
                if (responseCode != 200) {
                    Log.d(TAG, "请求文件大小状态码异常：" + responseCode);
                    close(response);
                    error(ErrorType.NETWORK_ERROR);
                    return;
                }

                mFileLength = response.body().contentLength();
                close(response);

                String recordKey = MD5.encrypt(mPoint.getUrl());
                boolean showContinueDownload = false;

                if (mTmpFile.exists() && mTmpFile.length() == mFileLength) {
                    DownloadRecord downloadRecord = DownloadDBHelper.getInstance().getRecord(recordKey);
                    if (downloadRecord != null && downloadRecord.fileLength == mFileLength) {
                        List<DownloadSubProcess> allSubProcess = DownloadDBHelper.getInstance().getSubProcess(recordKey);
                        if (allSubProcess != null && !allSubProcess.isEmpty()) {
                            showContinueDownload = true;


                            Log.d(TAG, "继续上次下载，上次下载线程总数:" + CollectionUtils.size(allSubProcess));
                            for (DownloadSubProcess subProcess : allSubProcess) {
                                mSavedSize.addAndGet(subProcess.currentIdx - subProcess.startIdx);
                            }

                            onStart();
                            for (DownloadSubProcess subProcess : allSubProcess) {
                                if (subProcess.currentIdx == subProcess.endIdx + 1) {
                                    // 已经下载完了
                                    Log.d(TAG, String.format("上次下载，子线程 %s 已经完成", subProcess.subId));
                                } else {
                                    downloadFileByRange(subProcess);
                                }
                            }
                        }
                    }
                }

                if (!showContinueDownload) {
                    if (!mTmpFile.getParentFile().exists()) {
                        mTmpFile.getParentFile().mkdirs();
                    }
                    if (mTmpFile.exists()) {
                        mTmpFile.delete();
                    }
                    DownloadRecord oldDownloadRecord = DownloadDBHelper.getInstance().getRecord(recordKey);
                    if (oldDownloadRecord != null) {
                        DownloadDBHelper.getInstance().deleteRecord(recordKey);
                        DownloadDBHelper.getInstance().clearSubProcess(recordKey);
                    }

                    DownloadRecord downloadRecord = new DownloadRecord();
                    downloadRecord.key = recordKey;
                    downloadRecord.downloadUrl = mPoint.getUrl();
                    downloadRecord.fileLength = mFileLength;
                    downloadRecord.filePath = mOutFile.getPath();

                    DownloadDBHelper.getInstance().saveRecord(downloadRecord);

                    List<DownloadSubProcess> allSubProcess = new ArrayList<>();
                    long blockSize = mFileLength / THREAD_COUNT;// 计算每个线程理论上下载的数量.
                    for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
                        long startIndex = threadId * blockSize; // 线程开始下载的位置
                        long endIndex = (threadId + 1) * blockSize - 1; // 线程结束下载的位置
                        if (threadId == (THREAD_COUNT - 1)) { // 如果是最后一个线程,将剩下的文件全部交给这个线程完成
                            endIndex = mFileLength - 1;
                        }

                        DownloadSubProcess subProcess = new DownloadSubProcess();
                        subProcess.recordKey = recordKey;
                        subProcess.subId = threadId;
                        subProcess.startIdx = startIndex;
                        subProcess.currentIdx = startIndex;
                        subProcess.endIdx = endIndex;
                        allSubProcess.add(subProcess);

                        DownloadDBHelper.getInstance().saveSubProcess(subProcess);
                    }

                    try {
                        RandomAccessFile tmpAccessFile = new RandomAccessFile(mTmpFile, "rw");
                        tmpAccessFile.setLength(mFileLength);
                    } catch (IOException e) {
                        Log.d(TAG, "构造临时文件异常：" + e.getMessage());
                        error(ErrorType.FILE_WRITE_ERROR);
                        return;
                    }

                    Log.d(TAG, "完全下载，下载线程总数:" + CollectionUtils.size(allSubProcess));
                    onStart();
                    for (DownloadSubProcess subProcess : allSubProcess) {
                        downloadFileByRange(subProcess);
                    }
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.d(TAG, "获取文件长度异常：" + e.getMessage());
                if (mStopRead) {
                    // do nothing
                } else {
                    error(ErrorType.NETWORK_ERROR);
                }
            }
        };

        Request request = new Request.Builder()
                .url(mPoint.getUrl())
                .build();
        Call call = sHttpClient.newCall(request);
        call.enqueue(requestCallback);
        mAllCaller.add(call);
    }

    public void reDownloadFileByRange(DownloadSubProcess subProcess) {
        int retryTimes = mRetryTimes.get(subProcess.subId, 0);
        Log.d(TAG, String.format("子线程 %s 第 %s 次重试, url: %s", subProcess.subId, retryTimes + 1, mPoint.getUrl()));
        mRetryTimes.put(subProcess.subId, retryTimes + 1);

        downloadFileByRange(subProcess);
    }

    public void downloadFileByRange(final DownloadSubProcess subProcess) {
        // 初始化已下载的量
        Log.d(TAG, "子线程开始下载 " + subProcess.subId + ", 开始字节:" + subProcess.startIdx + ", 当前字节:" + subProcess.currentIdx + ", 结束字节:" + subProcess.endIdx);
        Callback requestCallback = new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                int responseCode = response.code();
                if (responseCode != 206) {
                    // 分段相应状态码不为206
                    Log.d(TAG, subProcess.subId + " 分段下载状态码异常 :" + responseCode);
                    close(response);

                    if (mStopRead) {
                        Log.d(TAG, subProcess.subId + " 分段下载状态码异常，下载被取消，不再重试");
                    } else if (mRetryTimes.get(subProcess.subId, 0) < RETRY_TIMES) {
                        Log.d(TAG, subProcess.subId + " 分段下载状态码异常, 未到重试限制，重试");
                        reDownloadFileByRange(subProcess);
                    } else {
                        Log.d(TAG, subProcess.subId + " 分段下载状态码异常, 达到重试限制，抛出异常");
                        error(ErrorType.NETWORK_ERROR);
                    }
                    return;
                }

                InputStream is = null;
                RandomAccessFile tmpDownloadFile = null;

                try {
                    tmpDownloadFile = new RandomAccessFile(mTmpFile, "rw");// 获取前面已创建的文件.
                    tmpDownloadFile.seek(subProcess.currentIdx);// 文件写入的开始位置.
                } catch (IOException e) {
                    error(ErrorType.FILE_WRITE_ERROR);
                    return;
                }

                  /*  将网络流中的文件写入本地*/
                byte[] buffer = new byte[4096];
                int length = -1;

                try {
                    is = response.body().byteStream();// 获取流
                    while ((length = is.read(buffer)) > 0) {
                        tmpDownloadFile.write(buffer, 0, length);

                        subProcess.currentIdx += length;
                        DownloadDBHelper.getInstance().saveSubProcess(subProcess);

                        mSavedSize.addAndGet(length);
                        onProgress();

                        if (mSavedSize.longValue() == mFileLength) {
                            mTmpFile.renameTo(mOutFile);
                            onFinished();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (mStopRead) {
                        Log.d(TAG, subProcess.subId + " 分段下载读取字节流异常，下载被取消，不再重试");
                    } else if (mRetryTimes.get(subProcess.subId, 0) < RETRY_TIMES) {
                        Log.d(TAG, subProcess.subId + " 分段下载读取字节流异常，未到重试限制，重试");
                        reDownloadFileByRange(subProcess);
                    } else {
                        Log.d(TAG, subProcess.subId + " 分段下载读取字节流异常，达到重试限制，抛出异常");
                        error(ErrorType.NETWORK_ERROR);
                    }
                } finally {
                    close(response);
                    close(is);
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                if (mStopRead) {
                    Log.d(TAG, subProcess.subId + " 分段下载网络异常，下载被取消，不再重试");
                } else if (mRetryTimes.get(subProcess.subId, 0) < RETRY_TIMES) {
                    Log.d(TAG, subProcess.subId + " 分段下载网络异常，未到重试限制，重试");
                    reDownloadFileByRange(subProcess);
                } else {
                    Log.d(TAG, subProcess.subId + " 分段下载网络异常，达到重试限制，抛出异常");
                    error(ErrorType.NETWORK_ERROR);
                }
            }
        };
        Request request = new Request.Builder().header("RANGE", "bytes=" + subProcess.currentIdx + "-" + subProcess.endIdx)
                .url(mPoint.getUrl())
                .build();
        Call call = sHttpClient.newCall(request);
        call.enqueue(requestCallback);
        mAllCaller.add(call);
    }

    /**
     * 关闭资源
     *
     * @param closeables
     */
    private void close(Closeable... closeables) {
        int length = closeables.length;
        try {
            for (int i = 0; i < length; i++) {
                Closeable closeable = closeables[i];
                if (null != closeable)
                    closeables[i].close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (int i = 0; i < length; i++) {
                closeables[i] = null;
            }
        }
    }

    /**
     * 删除临时文件
     */
    private void cleanFile(File... files) {
        for (int i = 0, length = files.length; i < length; i++) {
            if (null != files[i])
                files[i].delete();
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        if (isDownloading()) {
            mStopRead = true;
            for (Call call : mAllCaller) {
                call.cancel();
            }

            onPause();
        }
    }

    /**
     * 取消
     */
    public void delete() {
        if (isDownloading()) {
            mStopRead = true;
            for (Call call : mAllCaller) {
                call.cancel();
            }
        }

        String recordKey = MD5.encrypt(mPoint.getUrl());
        DownloadRecord oldDownloadRecord = DownloadDBHelper.getInstance().getRecord(recordKey);
        if (oldDownloadRecord != null) {
            DownloadDBHelper.getInstance().deleteRecord(recordKey);
            DownloadDBHelper.getInstance().clearSubProcess(recordKey);

            cleanFile(mTmpFile, mOutFile);
            onDelete();
        }
    }

    private void error(int errorType) {
        mStopRead = true;
        for (Call call : mAllCaller) {
            call.cancel();
        }

        onError(errorType);
    }


    private void onStart() {
        Log.d(TAG, "on start");
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onStart(mPoint.getUrl(), mSavedSize.longValue(), mFileLength);
            }
        });
    }

    private void onProgress() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onProgress(mPoint.getUrl(), mSavedSize.longValue(), mFileLength);
            }
        });
    }

    private void onFinished() {
        status = STATUS_FINISHED;

        Log.d(TAG, "on finished");
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onFinished(mPoint.getUrl(), mOutFile.getPath());
            }
        });
    }

    private void onPause() {
        status = STATUS_PAUSE;

        Log.d(TAG, "on pause");
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onPause(mPoint.getUrl());
            }
        });
    }

    private void onError(final int errorType) {
        status = STATUS_ERROR;

        Log.d(TAG, "on error");
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onError(mPoint.getUrl(), errorType);
            }
        });
    }

    private void onDelete() {
        status = STATUS_READY;

        Log.d(TAG, "on cancel");
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onDelete(mPoint.getUrl());
            }
        });
    }

    public boolean isDownloading() {
        return status == STATUS_DOWNLOADING;
    }
}
