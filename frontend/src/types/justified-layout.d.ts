declare module 'justified-layout' {
  export interface LayoutBox { left: number; top: number; width: number; height: number; aspectRatio: number }
  export default function justifiedLayout(ratios: number[], options: {
    containerWidth: number; containerPadding: number; boxSpacing: number;
    targetRowHeight: number; targetRowHeightTolerance?: number; showWidows: boolean;
  }): { boxes: LayoutBox[]; containerHeight: number; widowCount: number }
}
