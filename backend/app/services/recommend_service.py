import asyncio
import json
from pathlib import Path

import numpy as np
from aiofiles import open
from fastapi import BackgroundTasks
from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.algorithm.tfidf_vectorizer import LightweightTfidfVectorizer, cosine_similarity
from app.core.config import BASE_DIR, SETTING
from app.middleware.logging import logger
from app.models.sql.book import Book
from app.services.cache_service import cache
from app.utils.distribute_lock import DistributedLock


class BookRecommendService:
    """
    书籍推荐服务,采用轻量级 TF-IDF 向量表示和余弦相似度计算
    """

    def __init__(
        self,
        data_path: Path = BASE_DIR / SETTING.RECOMMEND_BOOK_FILE_PATH,
        model_path: Path = BASE_DIR / SETTING.RECOMMEND_MODEL_FILE_PATH,
        top_k: int = 5,
    ):
        self.data_path = data_path
        # 确保模型路径以 .npz 结尾(np.savez 自动添加,但显式更安全)
        self.model_path = model_path
        self.top_k = top_k
        self.tfidf: LightweightTfidfVectorizer | None = None
        self.tfidf_matrix: np.ndarray | None = None
        self.book_ids: list[str] = []

    async def _load_books(self) -> list[dict]:
        async with open(self.data_path, encoding="utf-8") as f:
            content = await f.read()
            return json.loads(content)

    async def _preprocess_books(
        self, books: list[dict]
    ) -> tuple[list[str], list[list[str]]]:
        book_ids = []
        book_tags_list = []
        for book in books:
            book_ids.append(book["id"])
            tags = book["tags"].split(",") if book["tags"] else []
            tags = [t.strip() for t in tags if t.strip()]
            if not tags:
                tags = ["无标签"]
            book_tags_list.append(tags)
        return book_ids, book_tags_list

    async def train_and_save(self):
        """训练模型并保存到磁盘(使用 np.savez_compressed)"""
        if not self.data_path.exists():
            logger.info("数据不存在,请检查数据路径")
            return

        books = await self._load_books()
        self.book_ids, book_tags_list = await self._preprocess_books(books)
        corpus = [" ".join(tags) for tags in book_tags_list]

        self.tfidf = LightweightTfidfVectorizer(token_pattern=r"[^ ]+", lowercase=False)
        # ✅ 关键修复:使用 fit_transform 而非 fit
        self.tfidf_matrix = self.tfidf.fit_transform(corpus)

        # 保存为 .npz 文件
        np.savez_compressed(
            self.model_path,
            book_ids=np.array(self.book_ids, dtype=object),
            vocabulary=np.array(list(self.tfidf.vocabulary_.items()), dtype=object),
            idf=self.tfidf.idf_,
            n_docs=np.array([self.tfidf._n_docs]),
            tfidf_matrix=self.tfidf_matrix,
        )
        logger.info(f"模型训练完成并保存至 {self.model_path}")

    async def load_model(self):
        """从磁盘异步加载模型(避免阻塞事件循环)"""
        logger.info("开始加载模型")
        if not self.model_path.exists():
            logger.info("模型文件不存在,开始训练模型")
            await self.train_and_save()
            return

        try:
            # 在线程中执行 I/O,避免阻塞 asyncio 事件循环
            data = await asyncio.to_thread(np.load, self.model_path, allow_pickle=True)

            # 重建 LightweightTfidfVectorizer
            self.tfidf = LightweightTfidfVectorizer()
            vocab_items = data["vocabulary"]
            self.tfidf.vocabulary_ = {term: int(idx) for term, idx in vocab_items}
            self.tfidf.idf_ = data["idf"]
            self.tfidf._n_docs = int(data["n_docs"][0])

            self.tfidf_matrix = data["tfidf_matrix"]
            self.book_ids = data["book_ids"].tolist()

            logger.info("模型加载成功")
        except Exception as e:
            logger.error(f"模型加载失败: {e}")
            # 可选:回退到重新训练
            await self.train_and_save()

    def is_model_loaded(self) -> bool:
        return self.tfidf is not None and self.tfidf_matrix is not None

    @cache(exclude_kwargs=["background_tasks", "top_k", "database"])
    async def recommend(
        self,
        *,
        query_tags_str: str,
        top_k: int | None = None,
        background_tasks: BackgroundTasks,
        database: AsyncSession,
    ) -> list[tuple[str, float]]:
        k = top_k if top_k is not None else self.top_k
        query_tags = [t.strip() for t in query_tags_str.split(",") if t.strip()]
        if not query_tags:
            logger.info("没有输入标签,无法推荐")
            return []

        if not self.is_model_loaded():
            background_tasks.add_task(self.re_init, database)
            return []

        query_doc = " ".join(query_tags)
        query_vec = self.tfidf.transform([query_doc])  # shape: (1, d)

        # 检查是否所有词都是 OOV(全零向量)
        if np.count_nonzero(query_vec) == 0:
            return []

        # 使用自定义 cosine_similarity
        similarities = cosine_similarity(
            query_vec, self.tfidf_matrix
        )  # 返回 (n,) array
        top_indices = np.argsort(similarities)[-k:][::-1]
        return [(self.book_ids[i], float(similarities[i])) for i in top_indices]

    async def re_init(self, database: AsyncSession):
        """重新初始化模型"""
        logger.info("开始重新初始化模型")
        async with DistributedLock("recommend_lock_reinitialize") as lock:
            if lock:
                statement = select(Book.id, Book.tags)
                result = await database.exec(statement)
                books = [{"id": book[0], "tags": book[1]} for book in result.all()]

                async with open(self.data_path, "w", encoding="utf-8") as f:
                    await f.write(json.dumps(books, ensure_ascii=False))

                await self.train_and_save()
                logger.info("模型重新初始化完成")


book_recommend_service = BookRecommendService()
