from pydantic import BaseModel


class DiskStatus(BaseModel):
    """单个磁盘分区的状态"""

    mountpoint: str  # 挂载点 (如 C:\, /, /home)
    device: str  # 设备名 (如 /dev/sda1)
    fstype: str  # 文件系统类型 (如 NTFS, ext4)
    total: float
    used: float
    free: float
    percent: float


class SystemStatus(BaseModel):
    cpu_percent: float
    memory_total: float
    memory_used: float
    swap_total: float
    swap_used: float
    disks: list[DiskStatus]  # 所有磁盘分区列表
