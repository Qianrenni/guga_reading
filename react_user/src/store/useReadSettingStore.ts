import { create } from 'zustand';
import type { ReadSettings } from '@/types';

const defaultReadSettings: ReadSettings = {
  fontSize: 16,
  lineHeight: 32,
  letterSpacing: 2,
  fontFamily: 'Arial, PingFangSC-Regular, Microsoft Yahei, SimSun',
  color: '#333333',
  backgroundColor: '#ffffff',
};

const READ_SETTING_STORAGE_KEY = 'READ_SETTING_KEY';

function loadSettings(): ReadSettings {
  try {
    const saved = localStorage.getItem(READ_SETTING_STORAGE_KEY);
    if (saved) return JSON.parse(saved);
  } catch (error) {
    console.error('解析用户设置时出错:', error);
    // 忽略解析错误，使用默认设置
  }
  return { ...defaultReadSettings };
}

function saveSettings(settings: ReadSettings) {
  localStorage.setItem(READ_SETTING_STORAGE_KEY, JSON.stringify(settings));
}

interface ReadSettingState {
  readSettings: ReadSettings;

  updateReadSettings: (settings: Partial<ReadSettings>) => void;
  reset: () => void;
}

export const useReadSettingStore = create<ReadSettingState>((set, get) => ({
  readSettings: loadSettings(),

  updateReadSettings(settings: Partial<ReadSettings>) {
    const newSettings = { ...get().readSettings, ...settings };
    set({ readSettings: newSettings });
    saveSettings(newSettings);
  },

  reset() {
    set({ readSettings: { ...defaultReadSettings } });
    saveSettings(defaultReadSettings);
  },
}));
