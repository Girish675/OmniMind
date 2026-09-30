import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

export class ModelManager {
    constructor(modelsDir, db) {
        this.modelsDir = path.resolve(modelsDir || path.join(process.cwd(), 'models'));
        this.db = db;
        fs.mkdirSync(this.modelsDir, { recursive: true });
    }

    getModelsDirectory() {
        return this.modelsDir;
    }

    validateGguf(filePath) {
        try {
            if (!fs.existsSync(filePath)) return false;
            const stat = fs.statSync(filePath);
            if (stat.size < 8) return false;

            const fd = fs.openSync(filePath, 'r');
            const buffer = Buffer.alloc(8);
            fs.readSync(fd, buffer, 0, 8, 0);
            fs.closeSync(fd);

            // GGUF magic is 'GGUF' (0x46475547 in little endian)
            const magic = buffer.toString('utf8', 0, 4);
            const version = buffer.readUInt32LE(4);

            return magic === 'GGUF' && version >= 1 && version <= 3;
        } catch (e) {
            return false;
        }
    }

    extractMetadata(filePath) {
        if (!this.validateGguf(filePath)) {
            throw new Error('Invalid GGUF model file');
        }

        const fileName = path.basename(filePath);
        const stat = fs.statSync(filePath);

        // Guess/extract metadata from filename conventions and GGUF header
        let architecture = 'unknown';
        let quantization = 'unknown';
        let contextLength = 4096;
        let parameterCount = 0;

        const lowerName = fileName.toLowerCase();

        // Architecture detection
        if (lowerName.includes('qwen')) architecture = 'qwen2';
        else if (lowerName.includes('llama')) architecture = 'llama';
        else if (lowerName.includes('mistral')) architecture = 'mistral';
        else if (lowerName.includes('phi')) architecture = 'phi';
        else if (lowerName.includes('gemma')) architecture = 'gemma';

        // Quantization detection
        const quantMatch = lowerName.match(/(q[2-8]_[k_m_s_0-9]+|q[2-8]_[0-9]|f16|f32|bf16)/i);
        if (quantMatch) {
            quantization = quantMatch[1].toUpperCase();
        }

        // Parameter count detection
        const paramMatch = lowerName.match(/([0-9]+(?:\.[0-9]+)?)[bm]/i);
        if (paramMatch) {
            const num = parseFloat(paramMatch[1]);
            parameterCount = lowerName.includes('b') ? Math.round(num * 1e9) : Math.round(num * 1e6);
        }

        return {
            name: fileName.replace(/\.gguf$/i, ''),
            fileName,
            filePath,
            fileSize: stat.size,
            architecture,
            parameterCount,
            quantization,
            contextLength,
            author: architecture === 'qwen2' ? 'Qwen Team' : 'Community',
            license: architecture === 'qwen2' ? 'Apache-2.0' : 'Unknown',
            status: 'ready'
        };
    }

    async computeSha256(filePath, onProgress) {
        return new Promise((resolve, reject) => {
            const hash = crypto.createHash('sha256');
            const stream = fs.createReadStream(filePath, { highWaterMark: 128 * 1024 });

            stream.on('data', chunk => hash.update(chunk));
            stream.on('end', () => resolve(hash.digest('hex')));
            stream.on('error', err => reject(err));
        });
    }

    syncModelsDirectory() {
        if (!fs.existsSync(this.modelsDir)) return;
        const files = fs.readdirSync(this.modelsDir);
        const existingInDb = new Set(this.db.getAllModels().map(m => m.file_name));

        for (const file of files) {
            if (file.endsWith('.gguf') && !existingInDb.has(file)) {
                const fullPath = path.join(this.modelsDir, file);
                try {
                    if (this.validateGguf(fullPath)) {
                        const meta = this.extractMetadata(fullPath);
                        this.db.insertModel({
                            id: crypto.randomUUID(),
                            ...meta
                        });
                    }
                } catch (e) {
                    console.warn(`Could not register model ${file}:`, e.message);
                }
            }
        }
    }

    async importModelFile(sourceFilePath, fileName) {
        // Sanitize destination filename to avoid path traversal
        const sanitizedName = path.basename(fileName || sourceFilePath);
        if (!sanitizedName.endsWith('.gguf')) {
            throw new Error('Model file must have a .gguf extension');
        }

        const destPath = path.join(this.modelsDir, sanitizedName);
        const tempPath = path.join(this.modelsDir, `${sanitizedName}.part`);

        // Check if source exists
        if (!fs.existsSync(sourceFilePath)) {
            throw new Error(`Source file does not exist: ${sourceFilePath}`);
        }

        // Validate GGUF format before copying
        if (!this.validateGguf(sourceFilePath)) {
            throw new Error('Source file is not a valid GGUF model');
        }

        // Copy atomically via .part
        await fs.promises.copyFile(sourceFilePath, tempPath);
        if (fs.existsSync(destPath)) {
            fs.unlinkSync(destPath);
        }
        await fs.promises.rename(tempPath, destPath);

        const sha256 = await this.computeSha256(destPath);
        const metadata = this.extractMetadata(destPath);
        metadata.sha256 = sha256;

        return this.db.insertModel({
            id: crypto.randomUUID(),
            ...metadata
        });
    }

    deleteModel(id, activeLoadedModelId) {
        if (activeLoadedModelId === id) {
            throw new Error('Cannot delete model while it is currently loaded. Unload it first.');
        }

        const model = this.db.getModelById(id);
        if (!model) {
            throw new Error('Model not found');
        }

        // Secure path validation: ensure filePath is inside modelsDir
        const resolvedPath = path.resolve(model.file_path);
        if (resolvedPath.startsWith(this.modelsDir) && fs.existsSync(resolvedPath)) {
            try {
                fs.unlinkSync(resolvedPath);
            } catch (e) {
                console.warn(`Could not delete file ${resolvedPath}:`, e.message);
            }
        }

        this.db.deleteModel(id);
        return { success: true, id };
    }
}
