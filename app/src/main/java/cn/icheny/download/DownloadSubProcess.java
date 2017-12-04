package cn.icheny.download;

import io.github.yedaxia.sqliteutils.Table;

/**
 * Created by vincent on 2017/12/1.
 */

@Table(name = "download_sub_process", version = 1)
public class DownloadSubProcess {

    @Table.Column(name = "record_key", type = Table.Column.TYPE_STRING)
    public String recordKey;

    @Table.Column(name = "sub_id", type = Table.Column.TYPE_INTEGER)
    public Integer subId;

    @Table.Column(name = "start_idx", type = Table.Column.TYPE_LONG)
    public Long startIdx;

    @Table.Column(name = "current_idx", type = Table.Column.TYPE_LONG)
    public Long currentIdx;

    @Table.Column(name = "end_idx", type = Table.Column.TYPE_LONG)
    public Long endIdx;
}
