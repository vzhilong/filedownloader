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
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS);
        sHttpClient = builder.build();
    }

    private final int THREAD_COUNT = 4;//线程数
    private final int RETRY_TIMES = 20;

    private final int STATUS_READY = 0;
    private final int STATUS_DOWNLOADING = 1;


    private int status = STATUS_READY;
    private List<Call> mAllCaller;
    private FilePoint mPoint;
    private AtomicLong mSavedSize;

    private long mFileLength;
    private File mTmpFile;//临时占位文件
    private File mOutFile;
    private WrapperDownloadListener mListener;//下载回调监听
    private Handler mMainHandler;
    private boolean mStopRead = false;
    private SparseIntArray mRetryTimes = new SparseIntArray();

    /**
     * 任务管理器初始化数据
     *
     * @param point
     * @param listener
     */
    DownloadTask(FilePoint point, WrapperDownloadListener listener) {
        this.mPoint = point;
        this.mListener = listener;

        mTmpFile = new File(mPoint.getTmpFilePath());
        mOutFile = new File(mPoint.getOutFilePath());
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public synchronized void start(final DownloadProcess downloadProcess, final List<DownloadSubProcess> subProcessList) {
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

                boolean showContinueDownload = false;

                Log.d(TAG, String.format("请求文件大小 %s, 临时文件存在与否 %s, 临时文件大小 %s", mFileLength, mTmpFile.exists(), mTmpFile.exists() ? mTmpFile.length() : 0));
                if (mTmpFile.exists() && mTmpFile.length() == mFileLength) {
                    if (downloadProcess != null && downloadProcess.fileLength == mFileLength) {
                        if (subProcessList != null && !subProcessList.isEmpty()) {
                            showContinueDownload = true;

                            Log.d(TAG, "继续上次下载，上次下载线程总数:" + CollectionUtils.size(subProcessList));
                            for (DownloadSubProcess subProcess : subProcessList) {
                                mSavedSize.addAndGet(subProcess.currentIdx - subProcess.startIdx);
                            }

                            for (DownloadSubProcess subProcess : subProcessList) {
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
                    mListener.deleteProcess(mPoint.getUrl());

                    DownloadProcess newDownloadProcess = new DownloadProcess();
                    newDownloadProcess.downloadUrl = mPoint.getUrl();
                    newDownloadProcess.fileLength = mFileLength;
                    newDownloadProcess.filePath = mOutFile.getPath();


                    List<DownloadSubProcess> newSubProcessList = new ArrayList<>();
                    long blockSize = mFileLength / THREAD_COUNT;// 计算每个线程理论上下载的数量.
                    for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
                        long startIndex = threadId * blockSize; // 线程开始下载的位置
                        long endIndex = (threadId + 1) * blockSize - 1; // 线程结束下载的位置
                        if (threadId == (THREAD_COUNT - 1)) { // 如果是最后一个线程,将剩下的文件全部交给这个线程完成
                            endIndex = mFileLength - 1;
                        }

                        DownloadSubProcess subProcess = new DownloadSubProcess();
                        subProcess.downloadUrl = mPoint.getUrl();
                        subProcess.subId = threadId;
                        subProcess.startIdx = startIndex;
                        subProcess.currentIdx = startIndex;
                        subProcess.endIdx = endIndex;
                        newSubProcessList.add(subProcess);
                    }

                    mListener.saveProcess(mPoint.getUrl(), newDownloadProcess, newSubProcessList);

                    try {
                        RandomAccessFile tmpAccessFile = new RandomAccessFile(mTmpFile, "rw");
                        tmpAccessFile.setLength(mFileLength);
                    } catch (IOException e) {
                        Log.d(TAG, "构造临时文件异常：" + e.getMessage());
                        error(ErrorType.FILE_WRITE_ERROR);
                        return;
                    }

                    Log.d(TAG, "完全下载，下载线程总数:" + CollectionUtils.size(newSubProcessList));
                    for (DownloadSubProcess subProcess : newSubProcessList) {
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

    public void reDownloadFileByRange(final DownloadSubProcess subProcess) {
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int retryTimes = mRetryTimes.get(subProcess.subId, 0);
                Log.d(TAG, String.format("子线程 %s 第 %s 次延迟重试, url: %s", subProcess.subId, retryTimes + 1, mPoint.getUrl()));
                mRetryTimes.put(subProcess.subId, retryTimes + 1);

                downloadFileByRange(subProcess);
            }
        }, 1_500);
    }

    public void downloadFileByRange(final DownloadSubProcess subProcess) {
        // 初始化已下载的量
        Log.d(TAG, String.format("子线程 %s 开始下载, 开始字节: %s, 当前字节: %s, 结束字节: %s", subProcess.subId, subProcess.startIdx, subProcess.currentIdx, subProcess.endIdx));
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
                    Log.d(TAG, subProcess.subId + ", " + mPoint.getUrl() + ":" + e.getMessage());
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
                Log.d(TAG, subProcess.subId + ", " + mPoint.getUrl() + ":" + e.getMessage());
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
        if (status == STATUS_DOWNLOADING) {
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
        if (status == STATUS_DOWNLOADING) {
            mStopRead = true;
            for (Call call : mAllCaller) {
                call.cancel();
            }
        }

        mListener.deleteProcess(mPoint.getUrl());
        cleanFile(mTmpFile, mOutFile);
        onDelete();
    }

    private void error(int errorType) {
        mStopRead = true;
        for (Call call : mAllCaller) {
            call.cancel();
        }

        onError(errorType);
    }

    private void onProgress() {
        mListener.onProgress(mPoint.getUrl(), mSavedSize.longValue(), mFileLength);
    }


    private void onFinished() {
        status = STATUS_READY;

        Log.d(TAG, "on finished");
        mListener.onFinished(mPoint.getUrl(), mOutFile.getPath());
    }

    private void onPause() {
        status = STATUS_READY;

        Log.d(TAG, "on pause");
        mListener.onPause(mPoint.getUrl());
    }

    private void onError(int errorType) {
        status = STATUS_READY;

        Log.d(TAG, "on error");
        mListener.onError(mPoint.getUrl(), errorType);
    }

    private void onDelete() {
        status = STATUS_READY;

        Log.d(TAG, "on cancel");
        mListener.onDelete(mPoint.getUrl());
    }
}
