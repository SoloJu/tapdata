package io.tapdata.sybase.cdc.dto.start;

import java.util.List;

/**
 * @author GavinXiao
 * @description CdcStartVariables create by Gavin
 * @create 2023/7/14 14:58
 **/
public class CdcStartVariables {
    private List<SybaseFilterConfig> filterConfig;
    private SybaseSrcConfig srcConfig;
    private SybaseDstLocalStorage sybaseDstLocalStorage;
    private SybaseGeneralConfig sybaseGeneralConfig;
    private SybaseExtConfig extConfig;
    private List<SybaseReInitConfig> reInitConfigs;
    private SybaseLocalStrange sybaseLocalStrange;

    public static CdcStartVariables create() {
        return new CdcStartVariables();
    }

    public CdcStartVariables filterConfig(List<SybaseFilterConfig> filterConfig) {
        this.filterConfig = filterConfig;
        return this;
    }

    public CdcStartVariables srcConfig(SybaseSrcConfig srcConfig) {
        this.srcConfig = srcConfig;
        return this;
    }

    public CdcStartVariables extConfig(SybaseExtConfig extConfig) {
        this.extConfig = extConfig;
        return this;
    }

    public CdcStartVariables sybaseDstLocalStorage(SybaseDstLocalStorage sybaseDstLocalStorage) {
        this.sybaseDstLocalStorage = sybaseDstLocalStorage;
        return this;
    }

    public CdcStartVariables sybaseGeneralConfig(SybaseGeneralConfig sybaseGeneralConfig) {
        this.sybaseGeneralConfig = sybaseGeneralConfig;
        return this;
    }

    public List<SybaseFilterConfig> getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(List<SybaseFilterConfig> filterConfig) {
        this.filterConfig = filterConfig;
    }

    public SybaseSrcConfig getSrcConfig() {
        return srcConfig;
    }

    public void setSrcConfig(SybaseSrcConfig srcConfig) {
        this.srcConfig = srcConfig;
    }

    public SybaseDstLocalStorage getSybaseDstLocalStorage() {
        return sybaseDstLocalStorage;
    }

    public void setSybaseDstLocalStorage(SybaseDstLocalStorage sybaseDstLocalStorage) {
        this.sybaseDstLocalStorage = sybaseDstLocalStorage;
    }

    public SybaseGeneralConfig getSybaseGeneralConfig() {
        return sybaseGeneralConfig;
    }

    public void setSybaseGeneralConfig(SybaseGeneralConfig sybaseGeneralConfig) {
        this.sybaseGeneralConfig = sybaseGeneralConfig;
    }

    public SybaseExtConfig getExtConfig() {
        return extConfig;
    }

    public void setExtConfig(SybaseExtConfig extConfig) {
        this.extConfig = extConfig;
    }

    public List<SybaseReInitConfig> getReInitConfigs() {
        return reInitConfigs;
    }

    public void setReInitConfigs(List<SybaseReInitConfig> reInitConfigs) {
        this.reInitConfigs = reInitConfigs;
    }

    public CdcStartVariables reInitConfigs(List<SybaseReInitConfig> reInitConfigs) {
        this.reInitConfigs = reInitConfigs;
        return this;
    }

    public CdcStartVariables sybaseLocalStrange(SybaseLocalStrange sybaseLocalStrange) {
        this.sybaseLocalStrange = sybaseLocalStrange;
        return this;
    }

    public SybaseLocalStrange getSybaseLocalStrange() {
        return sybaseLocalStrange;
    }

    public void setSybaseLocalStrange(SybaseLocalStrange sybaseLocalStrange) {
        this.sybaseLocalStrange = sybaseLocalStrange;
    }
}
