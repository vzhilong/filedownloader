package cn.icheny.download;

import java.util.List;

/**
 * Created by vincent on 2017/12/5.
 */

public interface WrapperDownloadListener extends DownloadListener {
    void deleteProcess(String url);

    void saveProcess(String url, DownloadProcess downloadProcess, List<DownloadSubProcess> subProcessList);
}
