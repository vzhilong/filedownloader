package cn.icheny.download;

import io.github.yedaxia.sqliteutils.Table;

/**
 * Created by vincent on 2017/12/1.
 */

@Table(name = "download_record", version = 1)
public class DownloadRecord {

    @Table.Column(name = "id", type = Table.Column.TYPE_INTEGER, isPrimaryKey = true)
    public Integer id;

    @Table.Column(name = "download_url", type = Table.Column.TYPE_STRING, isUnique = true)
    public String downloadUrl;

    @Table.Column(name = "file_length", type = Table.Column.TYPE_LONG)
    public Long fileLength;
}
