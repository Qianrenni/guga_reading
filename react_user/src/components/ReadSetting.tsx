import { useEffect, useState } from 'react';
import { Slider, Select, Button } from 'antd';
import { RedoOutlined } from '@ant-design/icons';
import { themes, fontOptions } from '@/constant';
import { useReadSettingStore } from '@/store';
import type { ReadSettings } from '@/types';

export default function ReadSetting() {
  const useReadingSetting = useReadSettingStore();
  const [readSettings, setReadSettings] = useState<ReadSettings>({
    ...useReadingSetting.readSettings,
  });

  useEffect(() => {
    useReadingSetting.updateReadSettings(readSettings);
  }, [readSettings]);

  const updateTheme = (theme: (typeof themes)[keyof typeof themes]) => {
    setReadSettings((prev) => ({
      ...prev,
      color: theme.color,
      backgroundColor: theme.backgroundColor,
    }));
  };

  const reset = () => {
    useReadingSetting.reset();
    setReadSettings({ ...useReadingSetting.readSettings });
  };

  const fontOptionsList = Array.from(fontOptions).map((opt) => ({
    label: opt.label,
    value: opt.value,
  }));

  return (
    <div style={{ padding: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          gap: '0.5rem',
        }}
      >
        <h3 style={{ margin: 0 }}>阅读设置</h3>
        <RedoOutlined
          onClick={reset}
          title="恢复默认设置"
          style={{ cursor: 'pointer' }}
        />
      </div>

      {/* Preset themes */}
      <div>
        <p style={{ fontSize: '0.85rem', color: '#888', marginBottom: '0.5rem' }}>
          预设主题
        </p>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-evenly',
            gap: '0.5rem',
          }}
        >
          {Object.values(themes).map((theme) => (
            <div
              key={theme.color}
              onClick={() => updateTheme(theme)}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                cursor: 'pointer',
              }}
            >
              <span style={{ fontSize: '0.75rem' }}>{theme.label}</span>
              <div
                style={{
                  width: '2rem',
                  height: '2rem',
                  backgroundColor: theme.backgroundColor,
                  border: `2px solid ${theme.color}`,
                  borderRadius: '4px',
                }}
              />
            </div>
          ))}
        </div>
      </div>

      {/* Font family */}
      <div>
        <p style={{ fontSize: '0.85rem', color: '#888', marginBottom: '0.25rem' }}>
          字体
        </p>
        <Select
          value={readSettings.fontFamily}
          onChange={(value) =>
            setReadSettings((prev) => ({ ...prev, fontFamily: value }))
          }
          options={fontOptionsList}
          style={{ width: '100%' }}
        />
      </div>

      {/* Font size */}
      <div>
        <p style={{ fontSize: '0.85rem', color: '#888', marginBottom: '0.25rem' }}>
          字体大小: {readSettings.fontSize}px
        </p>
        <Slider
          min={16}
          max={32}
          value={readSettings.fontSize}
          onChange={(value) =>
            setReadSettings((prev) => ({
              ...prev,
              fontSize: value,
              lineHeight: prev.lineHeight + (value - prev.fontSize),
            }))
          }
        />
      </div>

      {/* Line height */}
      <div>
        <p style={{ fontSize: '0.85rem', color: '#888', marginBottom: '0.25rem' }}>
          行高: {readSettings.lineHeight}px
        </p>
        <Slider
          min={readSettings.fontSize}
          max={readSettings.fontSize * 3}
          value={readSettings.lineHeight}
          onChange={(value) =>
            setReadSettings((prev) => ({ ...prev, lineHeight: value }))
          }
        />
      </div>

      {/* Letter spacing */}
      <div>
        <p style={{ fontSize: '0.85rem', color: '#888', marginBottom: '0.25rem' }}>
          字间距: {readSettings.letterSpacing}px
        </p>
        <Slider
          min={2}
          max={4}
          step={0.5}
          value={readSettings.letterSpacing}
          onChange={(value) =>
            setReadSettings((prev) => ({ ...prev, letterSpacing: value }))
          }
        />
      </div>

      <Button onClick={reset} block>
        恢复默认设置
      </Button>
    </div>
  );
}
