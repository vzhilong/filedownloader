package cn.icheny.download;

import io.github.yedaxia.sqliteutils.Table;

/**
 * Created by vincent on 2017/12/1.
 */

@Table(name = "download_sub_process", version = 1)
public class DownloadSubProcess {

    @Table.Column(name = "id", type = Table.Column.TYPE_INTEGER)
    public Integer id;

    @Table.Column(name = "record_id", type = Table.Column.TYPE_INTEGER)
    public Integer recordId;

    @Table.Column(name = "start_idx", type = Table.Column.TYPE_LONG)
    public Long startIdx;

    @Table.Column(name = "end_idx", type = Table.Column.TYPE_LONG)
    public Long endIdx;
}
