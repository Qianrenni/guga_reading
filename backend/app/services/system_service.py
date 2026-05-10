import psutil

from app.models.domain.system import DiskStatus, SystemStatus


class SystemService:
    @staticmethod
    async def get_system_info():
        """
        获取服务器 CPU、内存、虚存和所有磁盘状态
        """
        # 1. CPU 占用率 (非阻塞)
        cpu_percent = psutil.cpu_percent(interval=None)

        # 2. 内存使用情况
        mem = psutil.virtual_memory()
        memory_total_gb = mem.total
        memory_used_gb = mem.used

        # 3. 虚存 (Swap) 使用情况
        swap = psutil.swap_memory()
        swap_total_gb = swap.total
        swap_used_gb = swap.used

        # 4. 磁盘使用情况 (所有分区)
        disk_statuses = []
        # 获取所有分区信息
        partitions = psutil.disk_partitions(
            all=False
        )  # all=False 排除虚拟/特殊文件系统如需全部可设为 True

        for partition in partitions:
            try:
                # 在某些系统中某些挂载点可能无法访问如光驱无盘需要异常处理
                usage = psutil.disk_usage(partition.mountpoint)

                disk_info = DiskStatus(
                    mountpoint=partition.mountpoint,
                    device=partition.device,
                    fstype=partition.fstype,
                    total=usage.total,
                    used=usage.used,
                    free=usage.free,
                    percent=usage.percent,
                )
                disk_statuses.append(disk_info)
            except (PermissionError, FileNotFoundError):
                # 跳过无法访问的分区
                continue

        # 构建并返回 Pydantic 模型实例
        result = SystemStatus(
            cpu_percent=cpu_percent,
            memory_total=memory_total_gb,
            memory_used=memory_used_gb,
            swap_total=swap_total_gb,
            swap_used=swap_used_gb,
            disks=disk_statuses,
        )

        return result
