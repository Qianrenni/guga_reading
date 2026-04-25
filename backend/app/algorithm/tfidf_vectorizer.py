from collections import defaultdict

import numpy as np


class LightweightTfidfVectorizer:
    def __init__(self):
        # 实际我们不用正则,直接按空格分(因为你已预处理为 ' '.join(tags))
        self.vocabulary_ = {}
        self.idf_ = None
        self._n_docs = 0

    def fit(self, raw_documents: list[str]):
        """raw_documents: list of 'tag1 tag2 tag3' strings"""
        doc_freq = defaultdict(int)
        self._n_docs = len(raw_documents)

        # 统计每个词在多少文档中出现
        for doc in raw_documents:
            unique_terms = set(doc.split())
            for term in unique_terms:
                doc_freq[term] += 1

        # 构建词汇表
        self.vocabulary_ = {
            term: idx for idx, term in enumerate(sorted(doc_freq.keys()))
        }
        n_terms = len(self.vocabulary_)

        # 计算 IDF: log(n_docs / df)
        self.idf_ = np.zeros(n_terms, dtype=np.float32)
        for term, idx in self.vocabulary_.items():
            df = doc_freq[term]
            self.idf_[idx] = np.log(self._n_docs / df)

        return self

    def transform(self, raw_documents: list[str]) -> np.ndarray:
        """返回 (n_docs, n_features) 的 TF-IDF 矩阵"""
        n_docs = len(raw_documents)
        n_features = len(self.vocabulary_)
        matrix = np.zeros((n_docs, n_features), dtype=np.float32)

        for i, doc in enumerate(raw_documents):
            terms = doc.split()
            if not terms:
                continue
            term_count = defaultdict(int)
            for term in terms:
                term_count[term] += 1

            for term, count in term_count.items():
                if term in self.vocabulary_:
                    idx = self.vocabulary_[term]
                    tf = count / len(terms)  # 也可用 raw count,TF-IDF 对比影响不大
                    matrix[i, idx] = tf * self.idf_[idx]

        return matrix

    def fit_transform(self, raw_documents: list[str]) -> np.ndarray:
        """先 fit 再 transform,返回 TF-IDF 矩阵"""
        self.fit(raw_documents)
        return self.transform(raw_documents)


def cosine_similarity(vec: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    """
    计算向量 vec 与矩阵 matrix 的余弦相似度
    - vec: shape (d,) 或 (1, d)
    - matrix: shape (n, d)
    Returns: (n,) 的相似度数组
    """
    # 处理 (1, d) 输入
    if vec.ndim == 2 and vec.shape[0] == 1:
        vec = vec[0]
    elif vec.ndim != 1:
        raise ValueError("vec must be 1D or (1, d)")

    norm_vec = np.linalg.norm(vec)
    norm_matrix = np.linalg.norm(matrix, axis=1)
    if norm_vec == 0 or np.all(norm_matrix == 0):
        return np.zeros(matrix.shape[0], dtype=np.float32)

    dot_product = np.dot(matrix, vec)
    similarities = dot_product / (norm_matrix * norm_vec)
    return similarities
