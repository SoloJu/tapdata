import os, sys
from log import logger

sys.path.append(os.path.dirname(os.path.abspath(__file__)) + "/../../lib")

from factory import newDB
from tapdata_cli.cli import (
    Pipeline,
    DataSource,
    Sink,
    Source
)


def smart_create_data(datasources):
    cdc_sources_config = {"cdc_sources": {}, "jobs": []}
    cdc_source = None
    # for s, v in datasources.items():
    #     if v.get("config", {}).get("__cdc_source", False):
    #         cdc_source = s
    #         break
    # if not cdc_source:
    #     logger.warn("{}, will skip smart cdc jobs", "NO __cdc_source found in config")
    #     return cdc_sources_config

    sink_datasources = []
    for s, v in datasources.items():
        if "source" in v.get("config", {}).get("__type", {}) and not v.get("__has_data"):
            sink_datasources.append(s)

    for sink in sink_datasources:
        for table in datasources[cdc_source]["tables"]:
            params = (cdc_source, get_test_table(cdc_source, table), sink, get_test_table(sink, table))
            logger.info("start a cdc job from source {}, table {} to sink {}, table {}", *params)
            p = Pipeline("smart_cdc_from_%s_%s_to_%s_%s" % params, mode="sync")
            p.readFrom(params[1]).writeTo(params[3])
            p.config({"type": "initial_sync"})
            p.start()
            p.wait_initial_sync()
            if sink == 'qa_sqlserver':
                db_client = newDB(params[3])
                db_client.open_cdc()
            cdc_sources_config["cdc_sources"][params[3]] = params[1]
            cdc_sources_config["jobs"].append(p)
        datasources[sink]["__has_data"] = True
    create_datasource()  # load schema, after new table created
    return cdc_sources_config
