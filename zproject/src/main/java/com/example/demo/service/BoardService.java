package com.example.demo.service;

import java.util.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import com.example.demo.dao.*;
import com.example.demo.dto.*;
import com.example.demo.dto.BoardDto.*;
import com.example.demo.entity.*;
import com.example.demo.exception.*;

@Service
public class BoardService {
	@Autowired
	private BoardDao boardDao;
	@Autowired
	private CommentDao commentDao;
	@Autowired
	private MemberBoardDao memberBoardDao;
	@Value("${zproject.pagesize}")
	private Integer pagesize;

	public Board write(BoardDto.Write dto, String loginId) {
		Board board = dto.toEntity().addWriter(loginId);
		boardDao.save(board);
		return board;
	}
	
	// 글읽기 : 글이 없으면 409(BNFE). 글이 있고 글쓴이이면 조회수 증가
	public BoardDto.Read read(Integer bno, String loginId) {
		BoardDto.Read dto = boardDao.findById(bno).orElseThrow(()->new BoardNotFoundException());
		if(loginId!=null && dto.getWriter().equals(loginId)==false) {
			boardDao.update(Board.builder().bno(bno).readCnt(1).build());
			dto.setReadCnt(dto.getReadCnt()+1);
		}
		dto.setComments(commentDao.findByBno(bno));
		return dto;
	}
	
	// 글목록 : 글이 없으면 빈 목록
	// 페이지 번호가 페이지의 개수보다 크다면 마지막 페이지로
	public BoardDto.Page list(Integer pageno, String writer) {
		Integer totalcount = boardDao.count(writer);
		Integer countOfPage = (totalcount-1)/pagesize + 1;
		if(pageno>countOfPage)
			pageno=countOfPage;
		else if(pageno<0)
			pageno=-pageno;
		else if(pageno==0)
			pageno=1;
		
		Integer start = (pageno-1) * pagesize + 1;
		Integer end = start * pagesize - 1;
		
		Map<String,Object> map = new HashMap<>();
		map.put("start", start);
		map.put("end", end);
		map.put("writer", writer);
		return new Page(pageno,pagesize,totalcount,boardDao.findAll(map));
	}
	
	// 글변경 : 실패 - 글이 없다(BoardNotFoundException), 글쓴이가 아니다(JobFailException)
	public Integer update(BoardDto.Update dto, String loginId) {
		String writer = boardDao.findWriterById(dto.getBno()).orElseThrow(()->new BoardNotFoundException());
		if(writer.equals(loginId)==false)
			throw new JobFailException("변경 권한이 없습니다");
		return boardDao.update(dto.toEntity());
	}
	
	// 글삭제 : 실패 - 글이 없다(BNFE), 글쓴이가 아니다(JFE)
	public Integer delete(Integer bno, String loginId) {
		String writer = boardDao.findWriterById(bno).orElseThrow(()->new BoardNotFoundException());
		if(writer.equals(loginId)==false)
			throw new JobFailException("삭제 권한이 없습니다");
		return boardDao.deleteById(bno);
	}

	// 좋아요 또는 싫어요
	public Integer goodOrBad(MemberBoardDto dto, String loginId) {
		String writer = boardDao.findWriterById(dto.getBno()).orElseThrow(()->new BoardNotFoundException());
		if(writer.equals(loginId))
			throw new JobFailException("좋아요/싫어요 권한이 없습니다");
		Map<String,Object> map = new HashMap<>();
		map.put("bno", dto.getBno());
		map.put("username", loginId);
		if(memberBoardDao.existsById(map)==true) {
			if(dto.getIsGood()==true)
				return boardDao.findGoodCntById(dto.getBno());
			return boardDao.findBadCntById(dto.getBno());
		} else {
			memberBoardDao.save(map);
			if(dto.getIsGood()==true) {
				boardDao.update(Board.builder().bno(dto.getBno()).goodCnt(1).build());
				return boardDao.findGoodCntById(dto.getBno());
			}
			boardDao.update(Board.builder().bno(dto.getBno()).badCnt(1).build());
			return boardDao.findBadCntById(dto.getBno());
		}
	}
}