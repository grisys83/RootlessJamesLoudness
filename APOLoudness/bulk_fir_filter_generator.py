import numpy as np
from scipy import signal
import wave
import struct
import os
import time
from datetime import datetime
import csv
import json

class BulkFIRFilterGenerator:
    """
    ISO 등라우드니스 곡선 기반 대량 FIR 필터 생성기
    
    개선사항:
    - firwin2 기반 안정적인 필터 설계
    - 1kHz 정규화로 일관된 기준점
    - 메모리 효율적인 배치 처리
    - 진행 상황 모니터링
    - 필터 품질 검증
    """
    
    def __init__(self, fs=48000, numtaps=4095):
        self.fs = fs
        self.numtaps = numtaps if numtaps % 2 == 1 else numtaps + 1  # 홀수 탭 보장
        
        # ISO 226 등라우드니스 곡선 데이터
        self.iso_freq = [20, 25, 31.5, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000]
        
        self.iso_curves = {
            0: [76.5517, 65.6189, 55.1228, 45.5340, 37.6321, 30.8650, 25.0238, 20.5100, 16.6458, 13.1160, 10.0883, 7.5436, 5.1137, 3.0589, 1.4824, 0.3029, -0.3026, -0.0103, 1.0335, -1.1863, -4.1116, -7.0462, -9.0260, -8.4944, -4.4829, 3.2817, 9.8291, 10.4757, 8.3813, 14.1000, 79.6500],
            10: [83.7500, 75.7579, 68.2089, 61.1365, 54.9638, 49.0098, 43.2377, 38.1338, 33.4772, 28.7734, 24.8417, 21.3272, 18.0522, 15.1379, 12.9768, 11.1791, 9.9918, 9.9996, 11.2621, 10.4291, 7.2744, 4.4508, 3.0404, 3.7961, 7.4583, 14.3483, 20.9841, 23.4306, 22.3269, 25.1700, 81.4700],
            20: [89.5781, 82.6513, 75.9764, 69.6171, 64.0178, 58.5520, 53.1898, 48.3809, 43.9414, 39.3702, 35.5126, 31.9922, 28.6866, 25.6703, 23.4263, 21.4825, 20.1011, 20.0052, 21.4618, 21.4013, 18.1515, 15.3844, 14.2559, 15.1415, 18.6349, 25.0196, 31.5227, 34.4256, 33.0444, 34.6700, 84.1800],
            40: [99.8539, 93.9444, 88.1659, 82.6287, 77.7849, 73.0825, 68.4779, 64.3711, 60.5855, 56.7022, 53.4087, 50.3992, 47.5775, 44.9766, 43.0507, 41.3392, 40.0618, 40.0100, 41.8195, 42.5076, 39.2296, 36.5090, 35.6089, 36.6492, 40.0077, 45.8283, 51.7968, 54.2841, 51.4859, 51.9600, 92.7700],
            60: [109.5113, 104.2279, 99.0779, 94.1773, 89.9635, 85.9434, 82.0534, 78.6546, 75.5635, 72.4743, 69.8643, 67.5348, 65.3917, 63.4510, 62.0512, 60.8150, 59.8867, 60.0116, 62.1549, 63.1894, 59.9616, 57.2552, 56.4239, 57.5699, 60.8882, 66.3613, 71.6640, 73.1551, 68.6308, 68.4300, 104.9200],
            80: [118.9900, 114.2326, 109.6457, 105.3367, 101.7214, 98.3618, 95.1729, 92.4797, 90.0892, 87.8162, 85.9166, 84.3080, 82.8934, 81.6786, 80.8634, 80.1736, 79.6691, 80.0121, 82.4834, 83.7408, 80.5867, 77.8847, 77.0748, 78.3124, 81.6182, 86.8087, 91.4062, 91.7361, 85.4068, 84.6700, 118.9500],
            100: [128.4100, 124.1500, 120.1100, 116.3800, 113.3500, 110.6500, 108.1600, 106.1700, 104.4800, 103.0300, 101.8500, 100.9700, 100.300, 99.8300, 99.6200, 99.500, 99.4400, 100.0100, 102.8100, 104.2500, 101.1800, 98.4800, 97.6700, 99.00, 102.300, 107.2300, 111.1100, 110.2300, 102.0700, 100.8300, 133.7300]
        }
        
        self.fine_curves = None
        self.setup_interpolated_curves()
    
    def setup_interpolated_curves(self):
        """세밀한 보간 곡선 초기화"""
        print("등라우드니스 곡선 보간 중...")
        
        # 1차 보간: 중간 폰 레벨 생성
        primary_curves = dict(self.iso_curves)
        for phon_level in [30, 50, 70, 90]:
            lower_phon = phon_level - 10
            upper_phon = phon_level + 10
            
            if lower_phon in self.iso_curves and upper_phon in self.iso_curves:
                weight = 0.5
                lower_curve = np.array(self.iso_curves[lower_phon])
                upper_curve = np.array(self.iso_curves[upper_phon])
                primary_curves[phon_level] = (
                    lower_curve * (1 - weight) + upper_curve * weight
                ).tolist()
        
        # 2차 세밀한 보간: 0.1 폰 단위
        self.fine_curves = {}
        sorted_phon_levels = sorted(primary_curves.keys())
        
        for phon_int in range(0, 1001):  # 0.0 ~ 100.0, 0.1 단위
            phon_val = phon_int / 10.0
            
            # 보간 구간 찾기
            lower_phon = None
            upper_phon = None
            
            for phon_level in sorted_phon_levels:
                if phon_level <= phon_val:
                    lower_phon = phon_level
                if phon_level >= phon_val:
                    upper_phon = phon_level
                    break
            
            if lower_phon is None:
                lower_phon = sorted_phon_levels[0]
            if upper_phon is None:
                upper_phon = sorted_phon_levels[-1]
            
            # 선형 보간
            if lower_phon == upper_phon:
                interpolated_curve = primary_curves[lower_phon][:]
            else:
                weight = (phon_val - lower_phon) / (upper_phon - lower_phon)
                lower_curve = np.array(primary_curves[lower_phon])
                upper_curve = np.array(primary_curves[upper_phon])
                interpolated_curve = (
                    lower_curve * (1 - weight) + upper_curve * weight
                ).tolist()
            
            self.fine_curves[phon_val] = [round(val, 4) for val in interpolated_curve]
        
        print(f"보간 완료: {len(self.fine_curves)}개 폰 레벨 생성")
    
    def design_single_filter(self, target_phon, reference_phon, window='hann', debug=False):
        """
        단일 FIR 필터 설계 (개선된 firwin2 방식)
        
        교과서적 설계 원리:
        1. 타겟과 참조 폰 레벨 간 SPL 차이 계산
        2. 1kHz 기준점으로 상대 이득 정규화
        3. firwin2를 사용한 임의 주파수 응답 근사
        4. 1kHz에서 정확히 0dB가 되도록 후처리
        """
        target_key = round(target_phon, 1)
        reference_key = round(reference_phon, 1)
        
        if target_key not in self.fine_curves or reference_key not in self.fine_curves:
            raise ValueError(f"폰 레벨을 찾을 수 없습니다: T={target_key}, R={reference_key}")
        
        # SPL 차이 계산
        target_curve = np.array(self.fine_curves[target_key])
        reference_curve = np.array(self.fine_curves[reference_key])
        db_diff = reference_curve - target_curve
        
        # 1kHz 기준점 (인덱스 17)
        idx_1khz = self.iso_freq.index(1000)
        reference_db_at_1khz = db_diff[idx_1khz]
        
        # 상대 이득 계산
        relative_gains_db = reference_db_at_1khz - db_diff
        relative_gains_linear = 10**(relative_gains_db / 20.0)
        
        # firwin2를 위한 주파수/이득 포인트 설정
        nyquist_freq = self.fs / 2.0
        
        # ISO 주파수와 이득을 쌍으로 처리
        iso_freq_gain_pairs = list(zip(self.iso_freq, relative_gains_linear))
        
        # 나이퀴스트 주파수 이하의 주파수만 선택
        valid_pairs = [(freq, gain) for freq, gain in iso_freq_gain_pairs if freq < nyquist_freq]
        
        if not valid_pairs:
            raise ValueError("유효한 ISO 주파수가 없습니다.")
        
        # 주파수와 이득 분리
        valid_freqs = [pair[0] for pair in valid_pairs]
        valid_gains = [pair[1] for pair in valid_pairs]
        
        # firwin2를 위한 주파수/이득 배열 구성 (실제 주파수 단위 사용)
        freq_array = [0.0] + valid_freqs + [nyquist_freq]
        gain_array = [valid_gains[0]] + valid_gains + [valid_gains[-1]]
        
        # numpy 배열로 변환
        freq_array = np.array(freq_array)
        gain_array = np.array(gain_array)
        
        # 중복 제거 (정렬 순서 유지)
        seen_freqs = []
        final_freqs = []
        final_gains = []
        
        for freq, gain in zip(freq_array, gain_array):
            if freq not in seen_freqs:
                seen_freqs.append(freq)
                final_freqs.append(freq)
                final_gains.append(gain)
        
        final_freqs = np.array(final_freqs)
        final_gains = np.array(final_gains)
        
        # firwin2 조건 엄격 검증
        if len(final_freqs) != len(final_gains):
            raise ValueError(f"주파수와 이득 배열 길이 불일치: {len(final_freqs)} != {len(final_gains)}")
        
        if final_freqs[0] != 0.0:
            raise ValueError(f"주파수 배열이 0으로 시작하지 않습니다: {final_freqs[0]}")
        
        if final_freqs[-1] != nyquist_freq:
            raise ValueError(f"주파수 배열이 fs/2로 끝나지 않습니다: {final_freqs[-1]} != {nyquist_freq}")
        
        if not np.all(np.diff(final_freqs) > 0):
            raise ValueError("주파수 배열이 순증가하지 않습니다.")
        
        # 디버깅 정보 출력
        if debug:
            print(f"\n=== 디버깅 정보 T={target_key}, R={reference_key} ===")
            print(f"샘플링 주파수: {self.fs}")
            print(f"나이퀴스트 주파수: {nyquist_freq}")
            print(f"유효한 ISO 주파수 개수: {len(valid_freqs)}")
            print(f"최종 주파수 배열: {final_freqs}")
            print(f"최종 이득 배열: {final_gains}")
            print(f"조건 확인:")
            print(f"  - 배열 길이 일치: {len(final_freqs) == len(final_gains)}")
            print(f"  - 첫 번째 주파수 = 0: {final_freqs[0] == 0.0}")
            print(f"  - 마지막 주파수 = fs/2: {final_freqs[-1] == nyquist_freq}")
            print(f"  - 순증가: {np.all(np.diff(final_freqs) > 0)}")
            print("=" * 50)
        
        try:
            # FIR 필터 설계 (실제 주파수 단위 사용, fs 파라미터 포함)
            fir_coeff = signal.firwin2(self.numtaps, final_freqs, final_gains, 
                                      window=window, fs=self.fs)
            
            # 1kHz 정규화
            w_1k, h_1k = signal.freqz(fir_coeff, worN=[1000], fs=self.fs)
            gain_1k = np.abs(h_1k[0])
            
            if gain_1k > 1e-9:
                fir_coeff /= gain_1k
            
            return fir_coeff, {
                'target_phon': target_phon,
                'reference_phon': reference_phon,
                'max_boost_db': float(20 * np.log10(np.max(relative_gains_linear))),
                'max_cut_db': float(20 * np.log10(np.min(relative_gains_linear))),
                'filter_length': len(fir_coeff)
            }
            
        except Exception as e:
            raise RuntimeError(f"필터 설계 실패 T={target_key}, R={reference_key}: {e}")
    
    def save_filter_to_wav(self, coefficients, filename, normalize=True):
        """FIR 계수를 WAV 파일로 저장"""
        if normalize:
            # 16비트 범위로 정규화
            max_val = np.max(np.abs(coefficients))
            if max_val > 0:
                scaled_coeff = np.int16(coefficients / max_val * 32767 * 0.9)  # 약간의 헤드룸
            else:
                scaled_coeff = np.zeros_like(coefficients, dtype=np.int16)
        else:
            scaled_coeff = np.int16(np.clip(coefficients * 32767, -32767, 32767))
        
        with wave.open(filename, 'w') as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(2)
            wav_file.setframerate(self.fs)
            
            for sample in scaled_coeff:
                wav_file.writeframes(struct.pack('h', sample))
    
    def generate_filter_range(self, target_range, reference_range, output_dir="filters", 
                             file_format="wav", save_metadata=True, debug_first=False):
        """
        지정된 범위의 모든 조합에 대해 필터 생성
        
        Args:
            target_range: (start, end, step) 튜플 - 타겟 폰 범위
            reference_range: (start, end, step) 튜플 - 참조 폰 범위  
            output_dir: 출력 디렉토리
            file_format: 출력 형식 ("wav", "csv", "both")
            save_metadata: 메타데이터 저장 여부
        """
        
        # 출력 디렉토리 생성
        os.makedirs(output_dir, exist_ok=True)
        
        # 범위 생성
        target_start, target_end, target_step = target_range
        ref_start, ref_end, ref_step = reference_range
        
        target_values = np.arange(target_start, target_end + target_step*0.001, target_step)
        ref_values = np.arange(ref_start, ref_end + ref_step*0.001, ref_step)
        
        target_values = np.round(target_values, 1)
        ref_values = np.round(ref_values, 1)
        
        total_filters = len(target_values) * len(ref_values)
        
        print(f"\n=== 대량 FIR 필터 생성 시작 ===")
        print(f"타겟 폰 범위: {target_start} ~ {target_end} (step: {target_step})")
        print(f"참조 폰 범위: {ref_start} ~ {ref_end} (step: {ref_step})")
        print(f"총 생성할 필터 수: {total_filters}")
        print(f"필터 길이: {self.numtaps} 탭")
        print(f"샘플링 주파수: {self.fs} Hz")
        print(f"출력 디렉토리: {output_dir}")
        
        # 메타데이터 수집
        metadata = {
            'generation_info': {
                'timestamp': datetime.now().isoformat(),
                'total_filters': total_filters,
                'fs': self.fs,
                'numtaps': self.numtaps,
                'target_range': target_range,
                'reference_range': reference_range
            },
            'filters': []
        }
        
        start_time = time.time()
        success_count = 0
        error_count = 0
        
        try:
            for i, target_phon in enumerate(target_values):
                for j, ref_phon in enumerate(ref_values):
                    current_filter = i * len(ref_values) + j + 1
                    
                    try:
                        # 필터 설계 (첫 번째 필터에만 디버그 적용)
                        is_first_filter = (current_filter == 1)
                        debug_mode = debug_first and is_first_filter
                        
                        fir_coeff, filter_info = self.design_single_filter(
                            target_phon, ref_phon, debug=debug_mode
                        )
                        
                        # 파일명 생성
                        base_filename = f"{target_phon:.1f}-{ref_phon:.1f}_filter"
                        
                        # 파일 저장
                        if file_format in ["wav", "both"]:
                            wav_filename = os.path.join(output_dir, f"{base_filename}.wav")
                            self.save_filter_to_wav(fir_coeff, wav_filename)
                        
                        if file_format in ["csv", "both"]:
                            csv_filename = os.path.join(output_dir, f"{base_filename}.csv")
                            np.savetxt(csv_filename, fir_coeff, delimiter=',', 
                                     header=f'FIR Filter Coefficients: {target_phon:.1f} -> {ref_phon:.1f} phon')
                        
                        # 메타데이터 추가
                        if save_metadata:
                            filter_info['filename'] = base_filename
                            metadata['filters'].append(filter_info)
                        
                        success_count += 1
                        
                        # 진행 상황 출력 (10% 단위)
                        if current_filter % max(1, total_filters // 10) == 0:
                            elapsed = time.time() - start_time
                            progress = current_filter / total_filters * 100
                            eta = elapsed / current_filter * (total_filters - current_filter)
                            
                            print(f"진행률: {progress:.1f}% ({current_filter}/{total_filters}) "
                                  f"| 성공: {success_count} | 실패: {error_count} "
                                  f"| 예상 남은 시간: {eta:.1f}초")
                    
                    except Exception as e:
                        error_count += 1
                        print(f"오류 발생 T={target_phon:.1f}, R={ref_phon:.1f}: {e}")
                        continue
        
        except KeyboardInterrupt:
            print(f"\n사용자에 의해 중단됨. 현재까지 {success_count}개 필터 생성완료.")
        
        # 메타데이터 저장
        if save_metadata and metadata['filters']:
            metadata_filename = os.path.join(output_dir, "filter_metadata.json")
            with open(metadata_filename, 'w', encoding='utf-8') as f:
                json.dump(metadata, f, indent=2, ensure_ascii=False)
        
        # 완료 보고
        total_time = time.time() - start_time
        
        print(f"\n=== 생성 완료 ===")
        print(f"성공: {success_count}개")
        print(f"실패: {error_count}개")
        print(f"총 소요 시간: {total_time:.1f}초")
        print(f"평균 필터당 시간: {total_time/max(1, success_count):.3f}초")
        
        return success_count, error_count

# === 사용 예제 ===
def main():
    """
    메인 실행 함수 - 다양한 사용 예제
    """
    
    # 필터 생성기 초기화
    generator = BulkFIRFilterGenerator(fs=48000, numtaps=4095)
    """
    print("1. 소규모 테스트 (5x5 = 25개 필터)")
    success, error = generator.generate_filter_range(
        target_range=(40.0, 42.0, 0.5),    # 40.0, 40.5, 41.0, 41.5, 42.0
        reference_range=(80.0, 82.0, 0.5), # 80.0, 80.5, 81.0, 81.5, 82.0
        output_dir="test_filters",
        file_format="both",  # WAV와 CSV 모두 저장
        save_metadata=True
    )
    # 대규모 생성 예제 (주석 처리됨)
    """
    
    print("\n2. 대규모 생성 (원본과 유사한 범위)")
    success, error = generator.generate_filter_range(
        target_range=(40.0, 85.0, 1),    # 40.0~90.0, 1.0 단위 (51개)
        reference_range=(75.0, 85.0, 1.0), # 80.0~90.0, 1.0 단위 (11개)
        output_dir="bulk_filters",          # 총 51 x 11 = 561개 필터
        file_format="wav",                  # WAV 파일만 저장 (용량 고려)
        save_metadata=True
    )
    """
    
    # 특정 조합만 생성하는 예제
    print("\n3. 특정 조합 단일 필터 생성")
    try:
        fir_coeff, info = generator.design_single_filter(60.0, 80.0)
        generator.save_filter_to_wav(fir_coeff, "single_60-80_filter.wav")
        print(f"단일 필터 생성 완료: {info}")
    except Exception as e:
        print(f"단일 필터 생성 실패: {e}")
    """
if __name__ == "__main__":
    main()