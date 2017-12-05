package cn.icheny.download;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 下载管理器，断点续传
 *
 * @author Cheny
 */
public class DownloadManager implements WrapperDownloadListener {

    private static final String TAG = "DownloadManager";
    private static DownloadManager mInstance;
    private String defaultDir;

    private Map<String, DownloadTask> mDownloadTasks;//文件下载任务索引，String为url,用来唯一区别并操作下载的文件

    private Map<String, DownloadProcess> mDownloadProcess;
    private Map<String, List<DownloadSubProcess>> mDownloadSubProcess;

    private Handler mWorkHandler;

    public DownloadManager() {
        mDownloadTasks = new HashMap<>();

        HandlerThread workThread = new HandlerThread("work-thread");
        workThread.start();
        mWorkHandler = new Handler(workThread.getLooper());
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

    public void initDownloadProcess() {
        mDownloadProcess = new HashMap<>();
        List<DownloadProcess> processList = DownloadDBHelper.getInstance().getAllProcess();
        if (processList != null && !processList.isEmpty()) {
            for (DownloadProcess downloadProcess : processList) {
                mDownloadProcess.put(downloadProcess.downloadUrl, downloadProcess);
            }
        }

        mDownloadSubProcess = new HashMap<>();
        List<DownloadSubProcess> subProcessList = DownloadDBHelper.getInstance().getAllSubProcessList();
        if (subProcessList != null && !subProcessList.isEmpty()) {
            for (DownloadSubProcess subProcess : subProcessList) {
                if (!mDownloadSubProcess.containsKey(subProcess.downloadUrl)) {
                    mDownloadSubProcess.put(subProcess.downloadUrl, new ArrayList<DownloadSubProcess>());
                }

                mDownloadSubProcess.get(subProcess.downloadUrl).add(subProcess);
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
    public void delete(String... urls) {
        //单任务取消或多任务取消下载
        for (int i = 0, length = urls.length; i < length; i++) {
            String url = urls[i];
            if (mDownloadTasks.containsKey(url)) {
                mDownloadTasks.get(url).delete();
            }
        }
    }

    /**
     * 添加下载任务
     */
    public void download(String url) {
        download(url, defaultDir, MD5.encrypt(url), "apk");
    }

    /**
     * 添加下载任务
     */
    public void download(String url, String filePath, String fileName, String fileExt) {
        FilePoint point = new FilePoint(url, filePath, fileName, fileExt);

        DownloadProcess downloadProcess = mDownloadProcess.get(url);
        List<DownloadSubProcess> subProcessList = mDownloadSubProcess.get(url);
        DownloadTask downloadTask = new DownloadTask(point, this);
        downloadTask.start(downloadProcess, subProcessList);
    }

    /**
     * 添加下载任务
     */
    public void readyForDownload(String url, String filePath, String fileName, String fileExt) {
        final FilePoint point = new FilePoint(url, filePath, fileName, fileExt);
        if (mDownloadProcess.containsKey(url)) {
            // do nothing
        } else {
            mWorkHandler.post(new Runnable() {
                @Override
                public void run() {
                    DownloadProcess downloadProcess = new DownloadProcess();
                    downloadProcess.downloadUrl = point.getUrl();
                    downloadProcess.filePath = point.getOutFilePath();
                    DownloadDBHelper.getInstance().saveProcess(downloadProcess);
                }
            });
        }

        onPause(point.getUrl());
    }

    @Override
    public void onProgress(final String url, final long sofar, final long total) {

    }

    @Override
    public void onFinished(final String url, final String filePath) {

    }

    @Override
    public void onPause(final String url) {

    }

    @Override
    public void onDelete(final String url) {

    }

    @Override
    public void onError(final String url, final int errorType) {

    }

    @Override
    public void deleteProcess(String url) {
        mDownloadProcess.remove(url);
        mDownloadSubProcess.remove(url);
        DownloadDBHelper.getInstance().deleteProcess(url);
        DownloadDBHelper.getInstance().deleteSubProcess(url);
    }

    @Override
    public void saveProcess(String url, DownloadProcess downloadProcess, List<DownloadSubProcess> subProcessList) {
        mDownloadProcess.put(url, downloadProcess);
        mDownloadSubProcess.put(url, subProcessList);

        DownloadDBHelper.getInstance().saveProcess(downloadProcess);
        for (DownloadSubProcess subProcess : subProcessList) {
            DownloadDBHelper.getInstance().saveSubProcess(subProcess);
        }
    }
}
