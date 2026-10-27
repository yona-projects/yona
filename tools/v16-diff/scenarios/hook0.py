"""웹훅 네 개만 만든다. 다른 시나리오(pr1 등)가 일으키는 이벤트의 웹훅을 보려고 그 앞에 돌린다. hookcompare.py로 대조한다."""
import hook1


def steps():
    return hook1.steps()[:8]
