import { SystemInfo } from '@guga-reading/types';
import { get } from '../utils';
export const useApiSystem = {
  prefix: '/system',
  getSystemInfo: async function () {
    return await get<SystemInfo>(`${this.prefix}/info`);
  },
};
