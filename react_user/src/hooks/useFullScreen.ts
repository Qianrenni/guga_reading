export function useFullScreen() {
  const toggle = () => {
    const doc = document.documentElement;
    if (document.fullscreenElement) {
      if (document.exitFullscreen) {
        document.exitFullscreen();
      }
    } else {
      if (doc.requestFullscreen) {
        doc.requestFullscreen();
      }
    }
  };
  return toggle;
}
