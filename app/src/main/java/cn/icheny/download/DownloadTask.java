package cn.icheny.download;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Response;

/**
 * Created by Cheny on 2017/4/29.
 */

public class DownloadTask extends Handler {

    private static final String TAG = "DownloadTask";
    private final int THREAD_COUNT = 4;//线程数

    private final int MSG_ON_PROGRESS = 1;//进度
    private final int MSG_ON_FINISH = 2;//完成下载
    private final int MSG_ON_PAUSE = 3;//暂停
    private final int MSG_ON_CANCEL = 4;//暂停
    private final int MSG_ON_ERROR = 5;

    private FilePoint mPoint;
    private long mFileLength;
    private boolean isDownloading = false;
    private int childCancelCount;//子线程取消数量
    private int childPauseCount;//子线程暂停数量
    private int childFinishCount; //子线程完成数量

    private long[] mProgress;
    private File[] mProcessFiles;
    private File mTmpFile;//临时占位文件
    private boolean pause;//是否暂停
    private boolean cancel;//是否取消下载
    private DownloadListener mListener;//下载回调监听

    /**
     * 任务管理器初始化数据
     *
     * @param point
     * @param listener
     */
    DownloadTask(FilePoint point, DownloadListener listener) {
        this.mPoint = point;
        this.mListener = listener;
        this.mProgress = new long[THREAD_COUNT];
        this.mProcessFiles = new File[THREAD_COUNT];
    }

    /**
     * 任务回调消息
     *
     * @param msg
     */
    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case MSG_ON_PROGRESS://进度
                long progress = 0;
                for (int i = 0, length = mProgress.length; i < length; i++) {
                    progress += mProgress[i];
                }
                mListener.onProgress(mPoint.getUrl(), progress, mFileLength);
                break;
            case MSG_ON_PAUSE://暂停
                childPauseCount++;
                if (childPauseCount % THREAD_COUNT != 0) {
                    return;
                }
                resetStatus();

                mListener.onPause(mPoint.getUrl());
                break;
            case MSG_ON_FINISH://完成
                childFinishCount++;
                if (childFinishCount % THREAD_COUNT != 0) {
                    return;
                }
                resetStatus();

                File output = new File(mPoint.getFilePath(), mPoint.getFileName() + "." + mPoint.getFileExt());
                mTmpFile.renameTo(output);//下载完毕后，重命名目标文件名
                mListener.onFinished(mPoint.getUrl(), output.getPath());
                break;
            case MSG_ON_CANCEL://取消
                childCancelCount++;
                if (childCancelCount % THREAD_COUNT != 0) {
                    return;
                }
                resetStatus();

                mProgress = new long[THREAD_COUNT];
                mListener.onCancel(mPoint.getUrl());
                break;
        }
    }

    public synchronized void start() {
        try {
            Log.e(TAG, "start: " + isDownloading + "\t" + mPoint.getUrl());
            if (isDownloading) {
                return;
            }
            isDownloading = true;
            HttpUtil.getContentLength(mPoint.getUrl(), new okhttp3.Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int responseCode = response.code();
                    if (responseCode != 200) {
                        close(response);
                        resetStatus();

                        Message msg = Message.obtain();
                        msg.what = MSG_ON_ERROR;
                        msg.arg1 = ErrorType.URL_ERROR;
                        msg.obj = "content length request, response code:" + responseCode;
                        sendMessage(msg);
                        return;
                    }

                    mFileLength = response.body().contentLength();
                    close(response);

                    boolean showContinueDownload = false;
                    mTmpFile = new File(mPoint.getFilePath(), mPoint.getFileName() + ".tmp");
                    if (mTmpFile.exists() && mTmpFile.length() == mFileLength) {
                        DownloadRecord downloadRecord = DownloadDBHelper.getInstance().getRecord(mPoint.getUrl());
                        if (downloadRecord != null && downloadRecord.fileLength == mFileLength) {
                            List<DownloadSubProcess> subProcessList = DownloadDBHelper.getInstance().getSubProcess(downloadRecord.id);
                            if (subProcessList != null && !subProcessList.isEmpty()) {
                                showContinueDownload = true;

                                for (DownloadSubProcess subProcess : subProcessList) {
                                    download(subProcess, subProcess.startIdx, subProcess.endIdx);
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
                        DownloadRecord oldDownloadRecord = DownloadDBHelper.getInstance().getRecord(mPoint.getUrl());
                        if (oldDownloadRecord != null) {
                            DownloadDBHelper.getInstance().deleteRecord(mPoint.getUrl());
                            DownloadDBHelper.getInstance().clearSubProcess(oldDownloadRecord.id);
                        }

                        DownloadRecord downloadRecord = new DownloadRecord();
                        downloadRecord.fileLength = mFileLength;
                        downloadRecord.downloadUrl = mPoint.getUrl();
                        long id = DownloadDBHelper.getInstance().saveRecord(downloadRecord);


                        List<DownloadSubProcess> subProcessList = new ArrayList<>();
                        long blockSize = mFileLength / THREAD_COUNT;// 计算每个线程理论上下载的数量.
                        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
                            long startIndex = threadId * blockSize; // 线程开始下载的位置
                            long endIndex = (threadId + 1) * blockSize - 1; // 线程结束下载的位置
                            if (threadId == (THREAD_COUNT - 1)) { // 如果是最后一个线程,将剩下的文件全部交给这个线程完成
                                endIndex = mFileLength - 1;
                            }

                            DownloadSubProcess subProcess = new DownloadSubProcess();
                            subProcess.recordId = downloadRecord.id;
                            subProcess.startIdx = startIndex;
                            subProcess.endIdx = endIndex;
                            subProcessList.add(subProcess);

                            DownloadDBHelper.getInstance().saveSubProcess(subProcess);
                        }

                        RandomAccessFile tmpAccessFile = new RandomAccessFile(mTmpFile, "rw");
                        tmpAccessFile.setLength(mFileLength);

                        for (DownloadSubProcess subProcess : subProcessList) {
                            download(subProcess, subProcess.startIdx, subProcess.endIdx);
                        }
                    }
                }

                @Override
                public void onFailure(Call call, IOException e) {
                    resetStatus();

                    Message msg = Message.obtain();
                    msg.what = MSG_ON_ERROR;
                    msg.arg1 = ErrorType.URL_ERROR;
                    msg.obj = "content length request, io exception:" + e.getMessage();
                    sendMessage(msg);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            resetStatus();
        }
    }

    public void download(final DownloadSubProcess subProcess, final long startIndex, final long endIndex) throws IOException {
        HttpUtil.downloadFileByRange(mPoint.getUrl(), startIndex, endIndex, new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() != 206) {// 206：请求部分资源成功码
                    resetStatus();
                    return;
                }
                InputStream is = response.body().byteStream();// 获取流
                RandomAccessFile tmpDownloadFile = new RandomAccessFile(mTmpFile, "rw");// 获取前面已创建的文件.
                tmpDownloadFile.seek(startIndex);// 文件写入的开始位置.
                  /*  将网络流中的文件写入本地*/
                byte[] buffer = new byte[1024 << 2];
                int length = -1;
                int total = 0;// 记录本次下载文件的大小

                while ((length = is.read(buffer)) > 0) {
                    if (cancel) {
                        sendEmptyMessage(MSG_ON_CANCEL);
                        return;
                    }
                    if (pause) {
                        sendEmptyMessage(MSG_ON_PAUSE);
                        return;
                    }
                    tmpDownloadFile.write(buffer, 0, length);
                    total += length;

                    DownloadDBHelper.getInstance().updateSubProcess(subProcess, startIndex + total);
                    //发送进度消息
                    mProgress[subProcess.id] = total;
                    sendEmptyMessage(MSG_ON_PROGRESS);
                    //Log.d(TAG, "thread:" + threadId + ", progress:" + length);
                }
                //发送完成消息
                sendEmptyMessage(MSG_ON_FINISH);
            }

            @Override
            public void onFailure(Call call, IOException e) {
                isDownloading = false;
            }
        });
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
        pause = true;
    }

    /**
     * 取消
     */
    public void cancel() {
        cancel = true;
        cleanFile(mTmpFile);
        if (!isDownloading) {
            if (null != mListener) {
                cleanFile(mProcessFiles);
                resetStatus();
                mListener.onCancel(mPoint.getUrl());
            }
        }
    }

    /**
     * 重置下载状态
     */
    private void resetStatus() {
        pause = false;
        cancel = false;
        isDownloading = false;
    }

    public boolean isDownloading() {
        return isDownloading;
    }
}
