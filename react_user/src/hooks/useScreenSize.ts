import { useEffect, useState } from 'react';

export function useScreenSize() {
  const [width, setWidth] = useState(window.innerWidth);
  const [height, setHeight] = useState(window.innerHeight);

  useEffect(() => {
    const handler = () => {
      setWidth(window.innerWidth);
      setHeight(window.innerHeight);
    };
    window.addEventListener('resize', handler);
    return () => window.removeEventListener('resize', handler);
  }, []);

  const isLessThan768 = width < 768;
  const isLessThan1200 = width < 1200;

  return { width, height, isLessThan768, isLessThan1200 };
}

export function useScreenSizeWidth(breakpoint: number) {
  const { width } = useScreenSize();
  return width < breakpoint;
}
