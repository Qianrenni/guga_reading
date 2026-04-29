/**
 * 设置当前页面标题
 * @param title string - 页面标题
 */
export function useTitle(title: string) {
  document.title = title;
}
export function toggleFullScreen() {
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
}
