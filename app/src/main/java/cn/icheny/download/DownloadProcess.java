package cn.icheny.download;

import io.github.yedaxia.sqliteutils.Table;

/**
 * Created by vincent on 2017/12/1.
 */

@Table(name = "download_process", version = 1)
public class DownloadProcess {

    @Table.Column(name = "download_url", type = Table.Column.TYPE_STRING, isUnique = true)
    public String downloadUrl;

    @Table.Column(name = "file_length", type = Table.Column.TYPE_LONG)
    public Long fileLength;

    @Table.Column(name = "file_path", type = Table.Column.TYPE_STRING)
    public String filePath;
}
