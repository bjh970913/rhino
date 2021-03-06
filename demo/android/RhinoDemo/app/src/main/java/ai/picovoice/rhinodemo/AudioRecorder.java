/*
 * Copyright 2018 Picovoice Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.picovoice.rhinodemo;


import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Records audio from microphone in a format that can be processed by suite of technologies developed
 * at Picovoice.
 */
class AudioRecorder {
    private static final String TAG = "PV_AUDIO_RECORDER";

    private final AudioConsumer audioConsumer;

    private AtomicBoolean started = new AtomicBoolean(false);
    private AtomicBoolean stop = new AtomicBoolean(false);
    private AtomicBoolean stopped = new AtomicBoolean(false);

    private class RecordTask implements Callable<Void> {
        @Override
        public Void call() throws Exception {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            record();
            return null;
        }
    }

    /**
     * Constructor.
     * @param audioConsumer The consumer for recorded audio.
     */
    AudioRecorder(AudioConsumer audioConsumer) {
        this.audioConsumer = audioConsumer;
    }

    /**
     * Starts the recording of audio.
     */
    void start() {
        if (started.get()) {
            return;
        }
        started.set(true);

        Executors.newSingleThreadExecutor().submit(new RecordTask());
    }

    /**
     * Stops the recording of audio.
     * @throws InterruptedException On failure.
     */
    void stop() throws InterruptedException {
        if (!started.get()) {
            return;
        }

        stop.set(true);
        while (!stopped.get()) {
            Thread.sleep(10);
        }

        started.set(false);
    }

    private void record() throws Exception {
        final int minBufferSize = AudioRecord.getMinBufferSize(
                audioConsumer.getSampleRate(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        final int bufferSize = Math.max(audioConsumer.getSampleRate() / 2, minBufferSize);

        short[] frame = new short[audioConsumer.getFrameLength()];

        AudioRecord record = null;
        try {
            record = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    audioConsumer.getSampleRate(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);
            record.startRecording();

            while (!stop.get()) {
                int numRead = record.read(frame, 0, frame.length);
                if (numRead == frame.length) {
                    audioConsumer.consume(frame);
                } else {
                    Log.w(TAG, "Not enough samples for the audio consumer.");
                }
            }
            record.stop();

        } finally {
            if (record != null) {
                record.release();
            }
            stopped.set(true);
        }
    }
}
