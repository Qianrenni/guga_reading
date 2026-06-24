import type { bgColors, fontOptions, textColors, themes } from '@/constant';

export interface ReadSettings {
  fontSize: number;
  lineHeight: number;
  letterSpacing: number;
  fontFamily: (typeof fontOptions)[number]['value'];
  color:
    | (typeof textColors)[number]['value']
    | (typeof themes)[keyof typeof themes]['color'];
  backgroundColor:
    | (typeof bgColors)[number]['value']
    | (typeof themes)[keyof typeof themes]['backgroundColor'];
}
