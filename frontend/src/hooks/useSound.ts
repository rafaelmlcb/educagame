import { useCallback, useRef } from 'react'

/**
 * Returns a play function that uses Web Audio API with cloneNode() for overlapping
 * playback (e.g. wheel click) without latency from waiting for previous sound to end.
 */
export function useSound(src: string | undefined): () => void {
  const audioRef = useRef<HTMLAudioElement | null>(null)

  const play = useCallback(() => {
    if (!src) return
    if (!audioRef.current) {
      const audio = new Audio(src)
      audioRef.current = audio
    }
    const clone = audioRef.current.cloneNode() as HTMLAudioElement
    clone.volume = 1
    clone.play().catch(() => {})
  }, [src])

  return play
}
